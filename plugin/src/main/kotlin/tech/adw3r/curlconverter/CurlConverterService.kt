package tech.adw3r.curlconverter

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Picks the engine (per settings) and keeps the JCEF browser alive only while it is being used. */
@Service(Service.Level.APP)
class CurlConverterService : Disposable {
    // Created on first use only: JcefEngine links against JCEF classes, which may be missing (see JcefSupport).
    private val jcef by lazy { JcefEngine().also { Disposer.register(this, it) } }
    @Volatile private var jcefCreated = false
    private val node = NodeEngine { CurlConverterSettings.getInstance().state.nodePath }
    @Volatile private var idleShutdown: ScheduledFuture<*>? = null

    /** Blocking; call from a background thread. Throws [EngineException] if no engine could run. */
    fun convert(curl: String, target: String, options: Map<String, String> = currentOptions()): ConversionResult {
        // JCEF delivers its callbacks through the EDT, so waiting for it there would block for the full timeout.
        check(!ApplicationManager.getApplication().isDispatchThread) { "curlconverter must not run on the EDT" }
        val input = StringUtil.convertLineSeparators(curl).trim()
        return when (CurlConverterSettings.getInstance().state.engine) {
            EngineMode.JCEF -> viaJcef(input, target, options)
            EngineMode.NODE -> node.convert(input, target, options)
            EngineMode.AUTO -> {
                if (!JcefSupport.isSupported()) return node.convert(input, target, options)
                try {
                    viaJcef(input, target, options)
                } catch (e: EngineException) {
                    if (node.findNode() == null) throw e
                    LOG.warn("JCEF engine failed, falling back to Node.js: ${e.message}")
                    node.convert(input, target, options)
                }
            }
        }
    }

    private fun currentOptions(): Map<String, String> =
        mapOf("impersonate" to CurlConverterSettings.getInstance().state.impersonate)

    private fun viaJcef(curl: String, target: String, options: Map<String, String>): ConversionResult {
        if (!JcefSupport.isSupported()) throw EngineException("JCEF is not available in this IDE")
        idleShutdown?.cancel(false)
        try {
            jcefCreated = true
            return jcef.convert(curl, target, options)
        } catch (e: LinkageError) {
            throw EngineException("JCEF classes could not be loaded: $e", e)
        } finally {
            idleShutdown = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule({ if (jcefCreated) jcef.shutdown() }, IDLE_MINUTES, TimeUnit.MINUTES)
        }
    }

    override fun dispose() {
        idleShutdown?.cancel(false)
    }

    companion object {
        private val LOG = logger<CurlConverterService>()
        private const val IDLE_MINUTES = 10L

        fun getInstance(): CurlConverterService = service()
    }
}
