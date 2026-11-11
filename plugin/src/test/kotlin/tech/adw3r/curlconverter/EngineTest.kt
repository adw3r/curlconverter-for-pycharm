package tech.adw3r.curlconverter

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Both engines, inside the real IDE runtime, must return exactly what the official curlconverter npm
 * package returns (src/test/resources/expected.json is generated from it by js/test/gen-expected.mjs).
 */
class EngineTest : BasePlatformTestCase() {
    private data class Case(val target: String, val curl: String, val expected: ConversionResult)

    private val cases: List<Case> by lazy {
        val text = javaClass.getResource("/expected.json")!!.readText()
        val root = JsonParser.parseString(text).asJsonObject
        assertEquals(CURLCONVERTER_VERSION, root.get("version").asString)
        root.getAsJsonArray("cases").map {
            val o = it.asJsonObject
            Case(o.get("target").asString, o.get("curl").asString, ConversionResult.fromJson(o))
        }
    }

    /** Engines are only ever called from background threads (JCEF answers via the EDT), so tests do the same. */
    private fun <T> offEdt(block: () -> T): T =
        PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread<T>(block), 120_000)

    private fun checkAll(engine: ConverterEngine) {
        val diffs = cases.mapNotNull { c ->
            val got = offEdt { engine.convert(c.curl, c.target) }
            if (got == c.expected) null else "[${c.target}] ${c.curl.take(60)}\n  expected: ${c.expected}\n  got:      $got"
        }
        assertTrue("${diffs.size}/${cases.size} differ (${engine.displayName}):\n" + diffs.joinToString("\n"), diffs.isEmpty())
    }

    fun testJcefIsAvailableInTestIde() = assertTrue("JCEF must be supported here, otherwise the JCEF test proves nothing", JcefSupport.isSupported())

    fun testJcefMatchesOfficialCurlconverter() {
        val engine = JcefEngine()
        try {
            val t0 = System.currentTimeMillis()
            offEdt { engine.convert("curl https://example.com", "python") }
            println("JCEF engine first conversion (incl. startup): ${System.currentTimeMillis() - t0} ms")
            checkAll(engine)
            engine.shutdown()
            // A fresh browser comes up after shutdown.
            assertTrue(offEdt { engine.convert("curl https://example.com", "python") }.code!!.contains("requests.get('https://example.com')"))
        } finally {
            engine.dispose()
        }
    }

    fun testNodeMatchesOfficialCurlconverter() = checkAll(NodeEngine { "" })

    fun testNodeMissingIsAnEngineError() {
        val error = offEdt { runCatching { NodeEngine { "/nonexistent/node" }.convert("curl https://e.com", "python") }.exceptionOrNull() }
        assertInstanceOf(error, EngineException::class.java)
        assertTrue(error!!.message!!.contains("/nonexistent/node"))
    }

    fun testImpersonateOptionReachesBothEngines() {
        val curl = "curl https://example.com -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0'"
        val jcef = JcefEngine()
        try {
            for (engine in listOf(jcef, NodeEngine { "" })) {
                fun code(options: Map<String, String>) = offEdt { engine.convert(curl, "python-curl-cffi", options) }.code!!
                assertTrue(engine.displayName, code(emptyMap()).contains("impersonate='firefox'"))
                assertTrue(engine.displayName, code(mapOf("impersonate" to "safari_ios")).contains("impersonate='safari_ios'"))
                assertFalse(engine.displayName, code(mapOf("impersonate" to "none")).contains("impersonate="))
            }
        } finally {
            jcef.dispose()
        }
    }

    fun testEveryTargetIsKnownToTheBundle() {
        val engine = NodeEngine { "" }
        for (t in Targets.ALL) {
            val r = offEdt { engine.convert("curl https://example.com", t.id) }
            assertFalse("${t.id}: ${r.error}", r.error?.startsWith("Unknown target") == true)
            assertTrue("${t.id}: ${r.error}", r.isSuccess)
        }
    }
}
