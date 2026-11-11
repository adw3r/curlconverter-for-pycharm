package tech.adw3r.curlconverter

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/** Converts [curl] off the EDT and hands successful code to [onCode] on the EDT; reports failures. */
internal fun convertInBackground(project: Project, curl: String, target: Target, onCode: (code: String, warnings: List<String>) -> Unit) {
    object : Task.Backgroundable(project, "Converting cURL to ${target.label}", true) {
        private var result: ConversionResult? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = true
            result = CurlConverterService.getInstance().convert(curl, target.id)
        }

        override fun onSuccess() {
            val r = result ?: return
            if (r.isSuccess) onCode(r.code!!, r.warnings)
            else Notifier.error(project, "curlconverter could not convert this command", r.error)
        }

        override fun onThrowable(error: Throwable) {
            when (error) {
                is ProcessCanceledException -> Unit
                is EngineException -> Notifier.error(project, "curlconverter is not available", error.message)
                else -> super.onThrowable(error)
            }
        }
    }.queue()
}
