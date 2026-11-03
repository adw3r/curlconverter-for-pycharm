package tech.adw3r.curlconverter

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

enum class EngineMode(private val label: String) {
    AUTO("Automatic (JCEF, then Node.js)"),
    JCEF("JCEF (built-in Chromium)"),
    NODE("Node.js");

    override fun toString(): String = label
}

@Service(Service.Level.APP)
@State(name = "CurlConverterSettings", storages = [Storage("curlconverter.xml")])
class CurlConverterSettings : PersistentStateComponent<CurlConverterSettings.State> {
    class State {
        var target: String = Targets.DEFAULT_ID
        var engine: EngineMode = EngineMode.AUTO
        var nodePath: String = ""
        var renameScratch: Boolean = true
        var showWarnings: Boolean = true
        /** curl_cffi impersonate=: "auto" (from the copied User-Agent), "none", or a curl_cffi browser alias. */
        var impersonate: String = IMPERSONATE_AUTO
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    val target: Target get() = Targets.byId(state.target)

    companion object {
        const val IMPERSONATE_AUTO = "auto"
        val IMPERSONATE_CHOICES = listOf(IMPERSONATE_AUTO, "chrome", "chrome_android", "edge", "firefox", "safari", "safari_ios", "none")

        fun getInstance(): CurlConverterSettings = service()
    }
}
