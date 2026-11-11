package tech.adw3r.curlconverter

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.DataFlavor

/** Tools menu: converts the cURL command on the clipboard into a new scratch file in the target language. */
class NewScratchFromClipboardAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        // false: "_" in "curl_cffi" is not a mnemonic marker
        e.presentation.setText("New ${CurlConverterSettings.getInstance().target.label} Scratch from Clipboard cURL", false)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val clip = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)?.trim()
        if (clip.isNullOrEmpty() || !clip.contains("curl")) {
            Notifier.info(project, "No cURL command on the clipboard", "Copy a request as cURL (bash) in DevTools or Burp first.")
            return
        }
        val target = CurlConverterSettings.getInstance().target
        convertInBackground(project, clip, target) { code, warnings ->
            val ext = Targets.extension(target.id) ?: "txt"
            val file = ScratchRootType.getInstance().createScratchFile(
                project, "curl.$ext", languageFor(ext), code, ScratchFileService.Option.create_new_always,
            )
            if (file == null) {
                Notifier.error(project, "Could not create a scratch file", null)
                return@convertInBackground
            }
            FileEditorManager.getInstance(project).openFile(file, true)
            Notifier.warnings(project, warnings)
        }
    }

    private fun languageFor(ext: String): Language =
        (FileTypeManager.getInstance().getFileTypeByExtension(ext) as? LanguageFileType)?.language ?: PlainTextLanguage.INSTANCE
}
