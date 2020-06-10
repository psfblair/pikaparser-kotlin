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

package net.phobot.parser.clause

import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.clause.nonterminal.FollowedBy
import net.phobot.parser.clause.nonterminal.NotFollowedBy
import net.phobot.parser.clause.nonterminal.OneOrMore
import net.phobot.parser.clause.terminal.CharSeq
import net.phobot.parser.clause.terminal.CharSet
import net.phobot.parser.clause.terminal.Nothing
import net.phobot.parser.clause.terminal.Start
import net.phobot.parser.utils.StringUtils
import java.util.*

/** Constructor functions for clause types where simply calling the constructor will not suffice.  */
object ClauseFactory {

    /** Construct a [OneOrMore] clause.  */
    fun oneOrMore(subClause: Clause): Clause {
        // It doesn't make sense to wrap these clause types in OneOrMore, but the OneOrMore should have
        // no effect if this does occur in the grammar, so remove it
        return if ( subClause is OneOrMore
                    || subClause is Nothing
                    || subClause is FollowedBy
                    || subClause is NotFollowedBy
                    || subClause is Start
                ) {
                    subClause
                } else OneOrMore(subClause)
    }

    /** Construct an [Optional] clause.  */
    fun optional(subClause: Clause): Clause {
        // Optional(X) -> First(X, Nothing)
        return First(subClause, Nothing())
    }

    /** Construct a [ZeroOrMore] clause.  */
    fun zeroOrMore(subClause: Clause): Clause {
        // ZeroOrMore(X) => Optional(OneOrMore(X)) => First(OneOrMore(X), Nothing)
        return optional(oneOrMore(subClause))
    }

    /** Construct a [FollowedBy] clause.  */
    fun followedBy(subClause: Clause): Clause {

        require( ! (subClause is FollowedBy || subClause is NotFollowedBy || subClause is Start) )
            { "${FollowedBy::class.simpleName}(${subClause::class.simpleName}) is nonsensical" }

        if (subClause is Nothing) {
            // FollowedBy(Nothing) -> Nothing (since Nothing always matches)
            return subClause
        }
        return FollowedBy(subClause)
    }

    /** Construct a [NotFollowedBy] clause.  */
    fun notFollowedBy(subClause: Clause): Clause {

        require( ! (subClause is FollowedBy || subClause is NotFollowedBy || subClause is Start) )
            { "${NotFollowedBy::class.simpleName}(${subClause::class.simpleName}) is nonsensical" }

        require(subClause !is Nothing)
            { "${NotFollowedBy::class.simpleName}(${Nothing::class.simpleName}) will never match anything" }

        return NotFollowedBy(subClause)
    }

    /** Construct a terminal that matches a string token.  */
    fun str(str: String): Clause {
        return if (str.length == 1) {
            CharSet(str[0])
        } else {
            CharSeq(str, ignoreCase = false)
        }
    }

    fun charRangeClause(rangeString: String): Clause {
        var text = StringUtils.unescapeString(rangeString)
        val invert = text.startsWith("^")
        if (invert) {
            text = text.substring(1)
        }
        return if (invert) cRange(text).invert() else cRange(text)
    }

    /**
     * Construct a terminal that matches a character range, specified using regexp notation without the square
     * brackets.
     */
    fun cRange(charRanges: String): CharSet {
        val invert = charRanges.startsWith("^")
        val charSets = ArrayList<CharSet>()
        var i = if (invert) 1 else 0
        while (i < charRanges.length) {
            val c = charRanges[i]
            if (i <= charRanges.length - 3 && charRanges[i + 1] == '-') {
                val cEnd = charRanges[i + 2]
                require(cEnd >= c) { "Char range limits out of order: $c, $cEnd" }
                charSets.add(cRange(c, cEnd))
                i += 2
            } else {
                charSets.add(CharSet(c))
            }
            i++
        }
        return if (charSets.size == 1) charSets[0] else CharSet(charSets)
    }

    /** Construct a terminal that matches a character range.  */
    fun cRange(minChar: Char, maxChar: Char): CharSet {
        require(maxChar >= minChar) { "maxChar < minChar" }
        val chars = CharArray(maxChar - minChar + 1)
        var c = minChar
        while (c <= maxChar) {
            chars[c - minChar] = c
            c++
        }
        return CharSet(chars)
    }
}
