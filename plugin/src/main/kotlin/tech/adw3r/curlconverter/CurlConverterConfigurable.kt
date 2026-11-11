package tech.adw3r.curlconverter

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class CurlConverterConfigurable : BoundConfigurable("curlconverter") {
    override fun createPanel(): DialogPanel {
        val s = CurlConverterSettings.getInstance().state
        return panel {
            row("Convert to:") {
                comboBox(Targets.ALL)
                    .bindItem({ Targets.byId(s.target) }, { s.target = it?.id ?: Targets.DEFAULT_ID })
            }
            row("curl_cffi impersonate:") {
                comboBox(CurlConverterSettings.IMPERSONATE_CHOICES)
                    .bindItem({ s.impersonate }, { s.impersonate = it ?: CurlConverterSettings.IMPERSONATE_AUTO })
            }.rowComment(
                "<b>auto</b> picks it from the copied User-Agent: Firefox → firefox, Safari → safari / safari_ios, " +
                    "Android Chrome → chrome_android, anything else → chrome. <b>none</b> leaves impersonate= out.",
            )
            row("Engine:") {
                comboBox(EngineMode.values().toList())
                    .bindItem({ s.engine }, { s.engine = it ?: EngineMode.AUTO })
            }.rowComment("JCEF runs curlconverter in the IDE's built-in Chromium; Node.js is only needed if JCEF is unavailable.")
            row("Node.js executable:") {
                textField()
                    .bindText(s::nodePath)
                    .align(AlignX.FILL)
                    .comment("Leave empty to look for <code>node</code> on PATH.")
            }
            row {
                checkBox("Rename scratch files to the target language (scratch.txt → scratch.py)")
                    .bindSelected(s::renameScratch)
            }
            row {
                checkBox("Show curlconverter warnings")
                    .bindSelected(s::showWarnings)
            }
            row {
                comment(
                    "Bundled <a href=\"https://github.com/curlconverter/curlconverter\">curlconverter</a> " +
                        "$CURLCONVERTER_VERSION (MIT), the converter behind curlconverter.com. Works offline.",
                )
            }
        }
    }
}
