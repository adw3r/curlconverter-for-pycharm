package tech.adw3r.curlconverter

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.jcef.JBCefApp

/**
 * JCEF availability check that is safe to call when the JCEF classes are absent: since 2026.2 they come
 * from the bundled "Web Browser (JCEF)" plugin, which the user can disable. Kept apart from [JcefEngine]
 * so that class is only loaded once JCEF is known to be present.
 */
internal object JcefSupport {
    private val LOG = logger<JcefSupport>()

    fun isSupported(): Boolean = try {
        JBCefApp.isSupported()
    } catch (t: Throwable) { // NoClassDefFoundError when the JCEF plugin is disabled
        LOG.info("JCEF is not available: $t")
        false
    }
}
