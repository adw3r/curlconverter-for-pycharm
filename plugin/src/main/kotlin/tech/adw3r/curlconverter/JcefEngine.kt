package tech.adw3r.curlconverter

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs curlconverter inside an off-screen JCEF (Chromium) browser — the same JavaScript + WebAssembly
 * that curlconverter.com runs, so no Node.js is needed. The browser is created on first use and kept
 * until [shutdown] (the service calls it after a few idle minutes).
 */
internal class JcefEngine : ConverterEngine, Disposable {
    override val displayName: String = "JCEF"

    private class Session(
        val browser: JBCefBrowser,
        val ready: CompletableFuture<String>,
        val pending: ConcurrentHashMap<Int, CompletableFuture<String>>,
    )

    private val lock = Any()
    @Volatile private var session: Session? = null
    private val ids = AtomicInteger()

    override fun convert(curl: String, target: String, options: Map<String, String>): ConversionResult {
        val s = session()
        try {
            await(s.ready, INIT_TIMEOUT_MS, "load curlconverter")
        } catch (e: EngineException) {
            discard(s) // next attempt starts from scratch
            throw e
        }
        val id = ids.incrementAndGet()
        val reply = CompletableFuture<String>()
        s.pending[id] = reply
        try {
            s.browser.cefBrowser.executeJavaScript(
                "window.__ccRun($id, ${GSON.toJson(target)}, ${GSON.toJson(curl)}, ${GSON.toJson(options)});",
                "curlconverter://run.js", 0,
            )
            return ConversionResult.fromJson(await(reply, CALL_TIMEOUT_MS, "convert"))
        } finally {
            s.pending.remove(id)
        }
    }

    /** Releases the browser (and its renderer process). The next [convert] creates a new one. */
    fun shutdown() {
        synchronized(lock) { session?.let { discard(it) } }
    }

    override fun dispose() = shutdown()

    private fun session(): Session {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            if (!JcefSupport.isSupported()) throw EngineException("JCEF is not available in this IDE")
            return onEdt { createSession() }.also { session = it }
        }
    }

    private fun discard(s: Session) {
        synchronized(lock) { if (session === s) session = null }
        val failure = EngineException("curlconverter browser was closed")
        s.ready.completeExceptionally(failure)
        s.pending.values.forEach { it.completeExceptionally(failure) }
        ApplicationManager.getApplication().invokeLater({ Disposer.dispose(s.browser) }, ModalityState.any())
    }

    private fun createSession(): Session {
        // Not created immediately: the JS query must be registered before the native browser exists.
        val browser = JBCefBrowser.createBuilder()
            .setOffScreenRendering(true)
            .setCreateImmediately(false)
            .build()
        val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val ready = CompletableFuture<String>()
        val pending = ConcurrentHashMap<Int, CompletableFuture<String>>()

        query.addHandler { message ->
            try {
                val obj = JsonParser.parseString(message).asJsonObject
                val id = obj.get("id").asInt
                if (id == 0) {
                    val error = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
                    if (error != null) ready.completeExceptionally(EngineException(error))
                    else ready.complete(obj.get("version")?.asString ?: "?")
                } else {
                    pending[id]?.complete(message)
                }
            } catch (t: Throwable) {
                LOG.warn("Bad message from curlconverter page: ${message.take(200)}", t)
            }
            null
        }

        val send = "window.__ccSend = function (s) { ${query.inject("s")} };"
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(b: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (!frame.isMain || ready.isDone) return
                b.executeJavaScript(send, "curlconverter://bridge.js", 0)
                b.executeJavaScript(Bundle.browserScript, "curlconverter://curlconverter.js", 0)
                b.executeJavaScript(READY_JS, "curlconverter://ready.js", 0)
            }

            override fun onLoadError(b: CefBrowser, frame: CefFrame, code: CefLoadHandler.ErrorCode, text: String?, url: String?) {
                if (frame.isMain) ready.completeExceptionally(EngineException("JCEF page failed to load: $code $text"))
            }
        }, browser.cefBrowser)

        Disposer.register(browser, query)
        browser.createImmediately()
        browser.loadHTML(PAGE)
        return Session(browser, ready, pending)
    }

    companion object {
        private val LOG = logger<JcefEngine>()
        private val GSON = Gson()
        private const val INIT_TIMEOUT_MS = 30_000L
        private const val CALL_TIMEOUT_MS = 20_000L

        private const val PAGE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>curlconverter</title></head><body></body></html>"

        private val READY_JS = """
            (function () {
              var api = globalThis.__curlconverter;
              function fail(e) { window.__ccSend(JSON.stringify({ id: 0, error: 'curlconverter failed to load: ' + String((e && e.message) || e) })); }
              if (!api) { fail('bundle did not run'); return; }
              window.__ccRun = function (id, target, curl, options) {
                var r;
                try { r = api.convert(target, curl, options); } catch (e) { r = { error: String((e && e.message) || e) }; }
                r.id = id;
                window.__ccSend(JSON.stringify(r));
              };
              api.ready.then(function () { window.__ccSend(JSON.stringify({ id: 0, version: api.version })); }, fail);
            })();
        """.trimIndent()

        private fun <T> onEdt(block: () -> T): T {
            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread) return block()
            var result: Result<T>? = null
            app.invokeAndWait({ result = runCatching(block) }, ModalityState.any())
            return result!!.getOrElse { throw EngineException("Could not start JCEF: ${it.message}", it) }
        }

        private fun <T> await(future: CompletableFuture<T>, timeoutMs: Long, what: String): T {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (true) {
                ProgressManager.checkCanceled()
                try {
                    return future.get(50, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    if (System.nanoTime() > deadline) throw EngineException("JCEF did not $what within ${timeoutMs / 1000} s")
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    throw cause as? EngineException ?: EngineException("JCEF failed to $what: ${cause?.message}", cause)
                }
            }
        }
    }
}
