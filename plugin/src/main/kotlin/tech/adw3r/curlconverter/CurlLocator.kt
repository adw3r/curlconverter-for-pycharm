package tech.adw3r.curlconverter

import com.intellij.openapi.util.TextRange

/**
 * Finds the cURL command around the caret: the closest line above (or at) the caret that starts with
 * `curl`, extended over `\`-continued lines and multi-line quotes, the way bash would read it.
 */
object CurlLocator {
    private val START = Regex("""^[ \t]*(curl(?:\.exe)?)(?=[ \t\\]|$)""")
    private const val MAX_LINES_BACK = 300

    fun findAt(text: CharSequence, caret: Int): TextRange? {
        val offset = caret.coerceIn(0, text.length)
        var lineStart = lineStartOf(text, offset)
        repeat(MAX_LINES_BACK) {
            val lineEnd = lineEndOf(text, lineStart)
            val match = START.find(text.subSequence(lineStart, lineEnd))
            if (match != null) {
                val start = lineStart + match.groups[1]!!.range.first
                val end = commandEnd(text, start)
                val tail = if (offset > end) text.subSequence(end, offset) else ""
                return if (offset >= lineStart && tail.isBlank()) TextRange(start, end) else null
            }
            if (lineStart == 0) return null
            lineStart = lineStartOf(text, lineStart - 1)
        }
        return null
    }

    /** End offset (exclusive, trailing whitespace trimmed) of the shell command starting at [start]. */
    fun commandEnd(text: CharSequence, start: Int): Int {
        val n = text.length
        var i = start
        loop@ while (i < n) {
            when (text[i]) {
                '\\' -> i += 2 // escaped char; "\\\n" is a line continuation
                '\'' -> i = skipQuoted(text, i + 1, '\'', escapes = false)
                '"' -> i = skipQuoted(text, i + 1, '"', escapes = true)
                '$' -> i = if (i + 1 < n && text[i + 1] == '\'') skipQuoted(text, i + 2, '\'', escapes = true) else i + 1
                '\n' -> break@loop
                else -> i++
            }
        }
        var end = minOf(i, n)
        while (end > start && text[end - 1].isWhitespace()) end--
        return end
    }

    /** Index just past the closing [quote], or the end of the text if it is unterminated. */
    private fun skipQuoted(text: CharSequence, from: Int, quote: Char, escapes: Boolean): Int {
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (escapes && c == '\\') i += 2
            else if (c == quote) return i + 1
            else i++
        }
        return text.length
    }

    private fun lineStartOf(text: CharSequence, offset: Int): Int {
        var i = minOf(offset, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun lineEndOf(text: CharSequence, lineStart: Int): Int {
        var i = lineStart
        while (i < text.length && text[i] != '\n') i++
        return i
    }
}
