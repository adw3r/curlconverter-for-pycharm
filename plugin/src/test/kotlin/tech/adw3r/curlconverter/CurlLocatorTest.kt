package tech.adw3r.curlconverter

import junit.framework.TestCase

class CurlLocatorTest : TestCase() {
    private fun find(textWithCaret: String): String? {
        val caret = textWithCaret.indexOf("<caret>")
        val text = textWithCaret.replace("<caret>", "")
        return CurlLocator.findAt(text, caret)?.substring(text)
    }

    fun testSingleLine() = assertEquals("curl https://e.com -H 'a: b'", find("curl https://e.com -H 'a: b'<caret>\n"))

    fun testChromeMultilineWithCaretAtEnd() {
        val cmd = "curl 'https://e.com/api' \\\n  -H 'accept: */*' \\\n  --data-raw '{\"a\":1}'"
        assertEquals(cmd, find("$cmd\n<caret>"))
    }

    fun testCaretInsideMiddleLine() {
        val cmd = "curl 'https://e.com/api' \\\n  -H 'acc<caret>ept: */*' \\\n  --data-raw 'x'"
        assertEquals(cmd.replace("<caret>", ""), find("print(1)\n$cmd\nprint(2)\n"))
    }

    fun testBurpAnsiCQuotesWithEscapedQuote() {
        val cmd = "curl -i -s -k -X \$'POST' --data-binary \$'user=a&pass=it\\'s ok' \$'https://t.local/login'"
        assertEquals(cmd, find("<caret>$cmd\nnext line"))
    }

    fun testLiteralNewlineInsideQuotes() {
        val cmd = "curl https://e.com --data-raw 'line1\nline2'"
        assertEquals(cmd, find("$cmd<caret>\nother"))
    }

    fun testIndentedCommand() = assertEquals("curl https://e.com", find("def f():\n    curl https://e.com<caret>\n"))

    fun testCaretOnUnrelatedLineBelow() = assertNull(find("curl https://e.com\nprint('x')<caret>\n"))

    fun testNoCurl() = assertNull(find("import requests\n<caret>"))

    fun testCurlAsPartOfWord() = assertNull(find("curly braces<caret>"))

    fun testUnterminatedQuoteRunsToEnd() = assertEquals("curl 'https://e.com\nrest", find("curl 'https://e.com\nrest<caret>"))
}
