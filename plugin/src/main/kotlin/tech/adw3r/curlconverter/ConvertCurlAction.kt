package tech.adw3r.curlconverter

import com.intellij.codeInsight.hint.HintManager
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
 * Editor context menu: replaces the selected cURL command — or the one at the caret — with code
 * produced by curlconverter. In a scratch file, also switches the scratch to the target language.
 */
class ConvertCurlAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val found = editor != null && e.project != null && findCommand(editor) != null
        e.presentation.isEnabledAndVisible = found
        // false: "_" in "curl_cffi" is not a mnemonic marker
        if (found) e.presentation.setText("Convert cURL to ${CurlConverterSettings.getInstance().target.label}", false)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val range = findCommand(editor)
        if (range == null) {
            HintManager.getInstance().showErrorHint(editor, "No cURL command at the caret")
            return
        }
        if (!EditorModificationUtil.checkModificationAllowed(editor)) return
        val document = editor.document
        if (!FileDocumentManager.getInstance().requestWriting(document, project)) return

        val curl = range.subSequence(document.charsSequence).toString()
        val stamp = document.modificationStamp
        val target = CurlConverterSettings.getInstance().target
        convertInBackground(project, curl, target) { code, warnings ->
            if (editor.isDisposed || document.modificationStamp != stamp) {
                Notifier.info(project, "cURL not replaced", "The file changed while the command was being converted. Run the action again.")
                return@convertInBackground
            }
            val replacement = indent(code.trimEnd('\n', ' '), lineIndent(editor, range.startOffset))
            WriteCommandAction.writeCommandAction(project).withName("Convert cURL").run<RuntimeException> {
                document.replaceString(range.startOffset, range.endOffset, replacement)
                editor.selectionModel.removeSelection()
                editor.caretModel.moveToOffset(range.startOffset)
            }
            Notifier.warnings(project, warnings)
            FileDocumentManager.getInstance().getFile(document)?.let { renameScratch(project, it, target) }
        }
    }

    companion object {
        private val LOG = logger<ConvertCurlAction>()

        /** The selection if it contains a cURL command, otherwise the command at the caret. */
        fun findCommand(editor: Editor): TextRange? {
            val text = editor.document.charsSequence
            val selection = editor.selectionModel
            if (selection.hasSelection()) {
                val range = TextRange(selection.selectionStart, selection.selectionEnd)
                val selected = range.subSequence(text)
                val lead = selected.indexOfFirst { !it.isWhitespace() }
                if (lead < 0 || !selected.contains("curl")) return null
                val trail = selected.length - selected.indexOfLast { !it.isWhitespace() } - 1
                return TextRange(range.startOffset + lead, range.endOffset - trail)
            }
            return CurlLocator.findAt(text, editor.caretModel.offset)
        }

        private fun lineIndent(editor: Editor, offset: Int): String {
            val doc = editor.document
            val lineStart = doc.getLineStartOffset(doc.getLineNumber(offset))
            return doc.charsSequence.subSequence(lineStart, offset).takeWhile { it == ' ' || it == '\t' }.toString()
        }

        /** Indents every line but the first (which sits where the command started). */
        private fun indent(code: String, indent: String): String =
            if (indent.isEmpty()) code
            else code.lines().mapIndexed { i, line -> if (i == 0 || line.isEmpty()) line else indent + line }.joinToString("\n")

        /** scratch.txt → scratch.py so the scratch gets highlighting and can be run. */
        fun renameScratch(project: Project, file: VirtualFile, target: Target) {
            if (!CurlConverterSettings.getInstance().state.renameScratch || !ScratchUtil.isScratch(file)) return
            val ext = Targets.extension(target.id) ?: return
            if (file.extension.equals(ext, ignoreCase = true)) return
            val parent = file.parent ?: return
            val base = file.nameWithoutExtension
            var name = "$base.$ext"
            var i = 1
            while (parent.findChild(name) != null) name = "${base}_${i++}.$ext"
            try {
                WriteAction.run<Exception> { file.rename(project, name) }
            } catch (e: Exception) {
                LOG.warn("Could not rename scratch ${file.name} to $name", e)
            }
        }
    }
}
