package com.example.ollo.util

import java.text.Normalizer
import java.util.regex.Pattern

object AsciiHelper {

    const val MAX_CARD_TEXT_LEN = 100

    /**
     * Checks if a string contains any non-ASCII characters (character code > 127).
     */
    fun hasNonAscii(text: String): Boolean {
        for (ch in text) {
            if (ch.code > 127) return true
        }
        return false
    }

    /**
     * Sanitizes a string to pure ASCII:
     * - Decomposes accented characters (e.g. é -> e, ñ -> n)
     * - Replaces common typographic punctuation (curly quotes, dashes, ellipses)
     * - Strips emoji and remaining non-ASCII characters
     */
    fun sanitizeToAscii(input: String): String {
        if (input.isEmpty()) return ""

        // 1. Replace common unicode symbols and smart quotes
        var text = input
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('—', '-')
            .replace('–', '-')
            .replace("…", "...")
            .replace('•', '*')
            .replace('°', ' ')

        // 2. Normalize and strip diacritics
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        text = pattern.matcher(normalized).replaceAll("")

        // 3. Keep only ASCII characters (printable 32..126 plus newline/tab)
        val sb = StringBuilder()
        for (ch in text) {
            val code = ch.code
            if (code in 32..126 || ch == '\n' || ch == '\t') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Encodes UI text (which may contain real newlines) to the wire format:
     * Line breaks are stored as literal two-character sequence `\n` (backslash + n).
     * Enforces the max 100 character cap.
     */
    fun encodeToWireText(uiText: String): String {
        val sanitized = sanitizeToAscii(uiText)
        val withLiteralNewlines = sanitized
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
        return withLiteralNewlines.take(MAX_CARD_TEXT_LEN)
    }

    /**
     * Decodes wire format (literal `\n` two-character sequence) back to UI newlines for editing.
     */
    fun decodeFromWireText(wireText: String): String {
        return wireText.replace("\\n", "\n")
    }
}
