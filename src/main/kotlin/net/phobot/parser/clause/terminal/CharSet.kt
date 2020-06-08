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

package net.phobot.parser.clause.terminal

import net.phobot.parser.memotable.Match
import net.phobot.parser.memotable.Match.Companion.NO_SUBCLAUSE_MATCHES
import net.phobot.parser.memotable.MemoKey
import net.phobot.parser.memotable.MemoTable
import net.phobot.parser.utils.StringUtils
import java.util.*

/** Terminal clause that matches a character or sequence of characters.  */
class CharSet : Terminal {

    private val charSet: MutableSet<Char> = HashSet()

    private val subCharSets: MutableList<CharSet> = ArrayList()

    private var invertMatch = false

    constructor(vararg chars: Char) : super() {
        for (i in chars.indices) {
            this.charSet.add(chars[i])
        }
    }

    constructor(vararg charSets: CharSet) : super() {
        for (charSet in charSets) {
            this.subCharSets.add(charSet)
        }
    }

    /** Invert in-place, and return this.  */
    fun invert(): CharSet {
        invertMatch = !invertMatch
        return this
    }

    private fun matchesInput(memoKey: MemoKey, input: String): Boolean {
        if (memoKey.startPos >= input.length) {
            return false
        }
        val matches = (charSet.isNotEmpty() //
                && invertMatch xor charSet.contains(input[memoKey.startPos]))
        if (matches) {
            return true
        }
        if (subCharSets.isNotEmpty()) {
            // SubCharSets may be inverted, so need to test each individually for efficiency,
            // rather than producing a large Set<Character> for all chars of an inverted CharSet
            for (subCharSet in subCharSets) {
                if (subCharSet.matchesInput(memoKey, input)) {
                    return true
                }
            }
        }
        return false
    }

    override fun determineWhetherCanMatchZeroChars() {}

    override fun match(memoTable: MemoTable, memoKey: MemoKey, input: String): Match? {
        return if (matchesInput(memoKey, input)) {
            // Terminals are not memoized (i.e. don't look in the memo table)
            Match(memoKey, length = 1, subClauseMatches = NO_SUBCLAUSE_MATCHES)
        } else null
    }

    private fun getCharSets(charSets: MutableList<CharSet>) {
        if (charSet.isNotEmpty()) {
            charSets.add(this)
        }
        for (subCharSet in subCharSets) {
            subCharSet.getCharSets(charSets)
        }
    }

    private fun toString(buf: StringBuilder) {
        val charsSorted = ArrayList(charSet)
        charsSorted.sort()
        val isSingleChar = !invertMatch && charsSorted.size == 1
        if (isSingleChar) {
            val c = charsSorted.iterator().next()
            buf.append('\'')
            buf.append(StringUtils.escapeQuotedChar(c))
            buf.append('\'')
        } else {
            if (charsSorted.isNotEmpty()) {
                buf.append('[')
                if (invertMatch) {
                    buf.append('^')
                }
                var i = 0
                while (i < charsSorted.size) {
                    val c = charsSorted[i]
                    buf.append(StringUtils.escapeCharRangeChar(c))
                    var j = i + 1
                    while (j < charsSorted.size && charsSorted[j].toInt() == c.toInt() + (j - i)) {
                        j++
                    }
                    if (j > i + 2) {
                        buf.append("-")
                        i = j - 1
                        buf.append(charsSorted[i])
                    }
                    i++
                }
                buf.append(']')
            }
        }
    }

    override fun toString(): String {
        return updateStringCacheIfNecessary {
            val charSets = ArrayList<CharSet>()
            getCharSets(charSets)
            val buf = StringBuilder()
            if (charSets.size > 1) {
                buf.append('(')
            }
            val startLen = buf.length
            for (charSet in charSets) {
                if (buf.length > startLen) {
                    buf.append(" | ")
                }
                charSet.toString(buf)
            }
            if (charSets.size > 1) {
                buf.append(')')
            }
            buf.toString()
        }
    }
}
