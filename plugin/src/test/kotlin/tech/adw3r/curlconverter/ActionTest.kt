package tech.adw3r.curlconverter

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.datatransfer.StringSelection

/** End-to-end through the registered actions, using the JCEF engine. */
class ActionTest : BasePlatformTestCase() {
    private val chromeCurl = """
        curl 'https://api.example.com/v1/login' \
          -H 'accept: application/json' \
          -H 'content-type: application/json' \
          -b 'session=abc; theme=dark' \
          --data-raw '{"user":"a","remember":true}'
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(testRootDisposable, PathManager.getConfigPath(), PathManager.getScratchPath())
        // Strict JCEF (no Node fallback), so these tests prove the in-IDE Chromium path end to end.
        CurlConverterSettings.getInstance().loadState(CurlConverterSettings.State().apply { engine = EngineMode.JCEF })
    }

    private fun waitFor(what: String, condition: () -> Boolean) =
        PlatformTestUtil.waitWithEventsDispatching("Timed out waiting for $what", condition, 60)

    fun testActionsAreRegistered() {
        val am = ActionManager.getInstance()
        assertInstanceOf(am.getAction("curlconverter.Convert"), ConvertCurlAction::class.java)
        assertInstanceOf(am.getAction("curlconverter.ScratchFromClipboard"), NewScratchFromClipboardAction::class.java)
    }

    fun testHiddenWhenNoCurlAtCaret() {
        myFixture.configureByText("a.py", "import requests\n<caret>")
        val presentation = myFixture.testAction(ConvertCurlAction())
        assertFalse(presentation.isEnabledAndVisible)
    }

    fun testConvertsIndentedCommandAtCaretInPythonFile() {
        val indented = chromeCurl.lines().joinToString("\n") { "    $it" }
        myFixture.configureByText("a.py", "def login():\n$indented<caret>\n\nprint('after')\n")
        val presentation = myFixture.testAction(ConvertCurlAction())
        assertEquals("Convert cURL to Python (curl_cffi)", presentation.text)
        waitFor("conversion") { !myFixture.editor.document.text.contains("curl '") }
        val text = myFixture.editor.document.text
        println(text)
        assertTrue(text.startsWith("def login():\n    from curl_cffi import requests\n\n    cookies = {\n        'session': 'abc',"))
        assertTrue(text.contains("    response = requests.post('https://api.example.com/v1/login', cookies=cookies, headers=headers, json=json_data, impersonate='chrome')"))
        assertTrue(text.endsWith("\n\nprint('after')\n"))
    }

    fun testConvertsSelectionOnly() {
        myFixture.configureByText("notes.txt", "before\n<selection>curl https://example.com/x -X DELETE</selection>\nafter\n")
        myFixture.testAction(ConvertCurlAction())
        waitFor("conversion") { !myFixture.editor.document.text.contains("curl https") }
        assertEquals(
            "before\nfrom curl_cffi import requests\n\nresponse = requests.delete('https://example.com/x', impersonate='chrome')\nafter\n",
            myFixture.editor.document.text,
        )
    }

    fun testScratchIsConvertedAndRenamedToPy() {
        val scratch = ScratchRootType.getInstance().createScratchFile(
            project, "scratch.txt", PlainTextLanguage.INSTANCE, chromeCurl, ScratchFileService.Option.create_new_always,
        )!!
        myFixture.openFileInEditor(scratch)
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.testAction(ConvertCurlAction())
        waitFor("conversion") { !FileDocumentManager.getInstance().getDocument(scratch)!!.text.contains("curl '") }
        assertTrue(scratch.name, scratch.name.matches(Regex("""scratch(_\d+)?\.py""")))
        assertTrue(FileDocumentManager.getInstance().getDocument(scratch)!!.text.startsWith("from curl_cffi import requests\n"))
    }

    fun testMultipartBecomesCurlMime() {
        myFixture.configureByText("upload.py", "curl https://example.com/upload -F 'file=@photo.jpg' -F 'description=avatar'<caret>\n")
        myFixture.testAction(ConvertCurlAction())
        waitFor("conversion") { !myFixture.editor.document.text.contains("curl https") }
        assertEquals(
            """
            from curl_cffi import CurlMime, requests

            multipart = CurlMime()
            multipart.addpart(name='file', local_path='photo.jpg')
            multipart.addpart(name='description', data=b'avatar')

            response = requests.post('https://example.com/upload', multipart=multipart, impersonate='chrome')

            """.trimIndent(),
            myFixture.editor.document.text,
        )
    }

    fun testImpersonateSettingIsUsed() {
        CurlConverterSettings.getInstance().state.impersonate = "safari"
        myFixture.configureByText("a.py", "curl https://example.com/x<caret>")
        myFixture.testAction(ConvertCurlAction())
        waitFor("conversion") { !myFixture.editor.document.text.contains("curl https") }
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("impersonate='safari'"))
    }

    fun testRequestsTargetStillAvailable() {
        CurlConverterSettings.getInstance().state.target = "python"
        myFixture.configureByText("a.py", "curl https://example.com/x<caret>")
        myFixture.testAction(ConvertCurlAction())
        waitFor("conversion") { !myFixture.editor.document.text.contains("curl https") }
        assertEquals("import requests\n\nresponse = requests.get('https://example.com/x')", myFixture.editor.document.text)
    }

    fun testNewScratchFromClipboard() {
        CopyPasteManager.getInstance().setContents(StringSelection(chromeCurl))
        val action = NewScratchFromClipboardAction()
        val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
        action.update(event)
        assertEquals("New Python (curl_cffi) Scratch from Clipboard cURL", event.presentation.text)
        action.actionPerformed(event)
        val fem = FileEditorManager.getInstance(project)
        waitFor("scratch") { fem.selectedFiles.any { it.name.startsWith("curl") } }
        val file = fem.selectedFiles.first { it.name.startsWith("curl") }
        try {
            assertTrue(file.name, file.name.endsWith(".py"))
            assertTrue(FileDocumentManager.getInstance().getDocument(file)!!.text.contains("requests.post('https://api.example.com/v1/login'"))
        } finally {
            fem.closeFile(file)
        }
    }
}
