package tech.adw3r.curlconverter

import java.nio.charset.StandardCharsets

/** Access to the curlconverter bundles shipped inside the plugin jar. */
internal object Bundle {
    const val BROWSER = "/curlconverter/curlconverter.browser.js"
    const val NODE = "/curlconverter/curlconverter.node.cjs"

    fun bytes(path: String): ByteArray =
        Bundle::class.java.getResourceAsStream(path)?.use { it.readBytes() }
            ?: throw EngineException("Bundled resource $path is missing from the plugin")

    val browserScript: String by lazy { String(bytes(BROWSER), StandardCharsets.UTF_8) }
}
