package tech.adw3r.curlconverter

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

internal object Notifier {
    private const val GROUP = "curlconverter"

    fun warnings(project: Project?, warnings: List<String>) {
        if (warnings.isEmpty() || !CurlConverterSettings.getInstance().state.showWarnings) return
        notify(project, "curlconverter warnings", warnings.joinToString("<br>") { "• " + html(it) }, NotificationType.WARNING)
    }

    fun error(project: Project?, title: String, message: String?) =
        notify(project, title, html(message ?: "Unknown error"), NotificationType.ERROR)

    fun info(project: Project?, title: String, message: String) =
        notify(project, title, html(message), NotificationType.INFORMATION)

    private fun notify(project: Project?, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup(GROUP)
            .createNotification(title, content, type)
            .addAction(NotificationAction.createSimple("Settings…") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, CurlConverterConfigurable::class.java)
            })
            .notify(project)
    }

    private fun html(s: String): String = StringUtil.escapeXmlEntities(s).replace("\n", "<br>")
}
