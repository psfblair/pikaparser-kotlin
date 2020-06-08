/*
 * //
 * // This file is part of the pika parser implementation allowing whitespace-sensitive syntax. It is based
 * // on the Java reference implementation at:
 * //
 * //     https://github.com/lukehutch/pikaparser
 * //
 * // The pika parsing algorithm is described in the following paper:
 * //
 * //     Pika parsing: reformulating packrat parsing as a dynamic programming algorithm solves the left recursion
 * //     and error recovery problems. Luke A. D. Hutchison, May 2020.
 * //     https://arxiv.org/abs/2005.06444* //
 * //
 * // This software is provided under the MIT license:
 * //
 * // Copyright (c) 2020 Paul Blair
 * // Based on pikaparser by Luke Hutchison, also licensed with the MIT license.
 * //
 * // Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * // documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * // the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * // and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * //
 * // The above copyright notice and this permission notice shall be included in all copies or substantial portions
 * // of the Software.
 * //
 * // THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * // TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * // THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * // CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * // DEALINGS IN THE SOFTWARE.
 * //
 *
 */

package net.phobot.parser.utils

/** String utilities.  */
object StringUtils {
    private const val NON_ASCII_CHAR = '■'

    /** Replace non-ASCII/non-printable char with a block.  */
    fun replaceNonASCII(c: Char): Char {
        return if (c.toInt() < 32 || c.toInt() > 126) NON_ASCII_CHAR else c
    }

    /** Replace all non-ASCII/non-printable characters with a block.  */
    private fun replaceNonASCII(str: String, buf: StringBuilder) {
        for (element in str) {
            buf.append(replaceNonASCII(element))
        }
    }

    /** Replace all non-ASCII/non-printable characters with a block.  */
    fun replaceNonASCII(str: String): String {
        val buf = StringBuilder()
        replaceNonASCII(str, buf)
        return buf.toString()
    }

    /** Convert a hex digit to an integer.  */
    private fun hexDigitToInt(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a'
            in 'A'..'F' -> c - 'F'
            else -> throw IllegalArgumentException("Illegal hex digit: $c")
        }
    }

    /** Unescape a single character.  */
    fun unescapeChar(escapedChar: String): Char {
        require(escapedChar.isNotEmpty()) { "Empty char string" }
        if (escapedChar.length == 1) {
            return escapedChar[0]
        }
        when (escapedChar) {
            "\\t" -> return '\t'
            "\\b" -> return '\b'
            "\\n" -> return '\n'
            "\\r" -> return '\r'
            "\\f" -> return '\u000C'
            "\\'" -> return '\''
            "\\\"" -> return '"'
            "\\\\" -> return '\\'
            else -> if (escapedChar.startsWith("\\u") && escapedChar.length == 6) {
                val c0 = hexDigitToInt(escapedChar[2])
                val c1 = hexDigitToInt(escapedChar[3])
                val c2 = hexDigitToInt(escapedChar[4])
                val c3 = hexDigitToInt(escapedChar[5])
                return (c0 shl 24 or (c1 shl 16) or (c2 shl 8) or c3).toChar()
            } else {
                throw IllegalArgumentException("Invalid character: $escapedChar")
            }
        }
    }

    /** Unescape a string.  */
    fun unescapeString(str: String): String {
        val buf = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\') {
                require(i != str.length - 1) {
                    // Should not happen
                    "Got backslash at end of quoted string"
                }
                buf.append(unescapeChar(str.substring(i, i + 2)))
                i++ // Consume escaped character
            } else {
                buf.append(c)
            }
            i++
        }
        return buf.toString()
    }

    /** Escape a character.  */
    private fun escapeChar(c: Char): String {
        return when {
            c.toInt() in 32..126 -> c.toString()
            c == '\n' -> "\\n"
            c == '\r' -> "\\r"
            c == '\t' -> "\\t"
            c == '\u000C' -> "\\f"
            c == '\b' -> "\\b"
            else -> "\\u" + String.format("%04x", c.toInt())
        }
    }

    /** Escape a single-quoted character.  */
    fun escapeQuotedChar(c: Char): String {
        return when (c) {
            '\'' -> "\\'"
            '\\' -> "\\\\"
            else -> escapeChar(c)
        }
    }

    /** Escape a character.  */
    private fun escapeQuotedStringChar(c: Char): String {
        return when (c) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            else -> escapeChar(c)
        }
    }

    /** Escape a character for inclusion in a character range pattern.  */
    fun escapeCharRangeChar(c: Char): String {
        return when (c) {
            '[' -> "\\["
            ']' -> "\\]"
            '^' -> "\\^"
            else -> escapeChar(c)
        }
    }

    /** Escape a string.  */
    fun escapeString(str: String): String {
        val buf = StringBuilder()
        for (c in str) {
            buf.append(if (c == '"') "\\\"" else escapeQuotedStringChar(c))
        }
        return buf.toString()
    }
}
