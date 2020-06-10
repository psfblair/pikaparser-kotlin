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

package net.phobot.parser.memotable

import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.clause.nonterminal.OneOrMore
import net.phobot.parser.clause.nonterminal.Seq
import java.util.*
import java.util.AbstractMap.SimpleEntry
import kotlin.collections.Map.Entry
import kotlin.streams.asStream
import kotlin.streams.toList

/** A complete match of a [Clause] at a given start position.  */
class Match

  /** Construct a new match.  */
  @JvmOverloads
  constructor(
      /** The [MemoKey].  */
      val memoKey: MemoKey,

      /** The length of the match.  */
      val length: Int = 0,

      /**
       * The subclause index of the first matching subclause (will be 0 unless [.labeledClause] is a
       * [First], and the matching clause was not the first subclause).
       */
      private val firstMatchingSubClauseIdx: Int = 0,

      /** The subclause matches.  */
      private val subClauseMatches: Array<Match?> = NO_SUBCLAUSE_MATCHES
  ) {

    /** Construct a new match of a nonterminal clause other than [First].
     *  or construct a new terminal match by leaving out subClauseMatches,
     *  or construct a new zero-length match without subclasses by also leaving out the length. */
    constructor(memoKey: MemoKey, length: Int, subClauseMatches: Array<Match?>) :
            this(memoKey, length = length, firstMatchingSubClauseIdx = 0, subClauseMatches = subClauseMatches)

    /**
     * Get subclause matches. Automatically flattens the right-recursive structure of [OneOrMore] and
     * [Seq] nodes, collecting the subclause matches into a single array of (AST node label, match) tuples.
     */
    fun getSubClauseMatches(): List<Entry<String?, Match>> {
        return when {
            // This is a terminal or an empty placeholder match returned by MemoTable.lookUpBestMatch
            subClauseMatches.isEmpty() -> emptyList()
            memoKey.clause is OneOrMore -> flattenRightRecursiveParseSubtree()
            memoKey.clause is First -> pairMatchWithASTNodeLabelOfFirstMatchingSubclause()
            else -> getLabeledSubclauseMatches()
        }
    }

    private fun flattenRightRecursiveParseSubtree(): ArrayList<Entry<String?, Match>> {
        val subClauseMatchesToUse = ArrayList<Entry<String?, Match>>()
        var curr = this

        while (curr.subClauseMatches.isNotEmpty()) {
            val subClauseMatch = curr.subClauseMatches[0]!!
            val entry = entryFor(subClauseMatch, curr.memoKey, 0)
            subClauseMatchesToUse.add(entry)

            if (curr.subClauseMatches.size == 1) {
                // Reached end of right-recursive matches
                break
            }
            curr = curr.subClauseMatches[1]!!
        }
        return subClauseMatchesToUse
    }

    private fun pairMatchWithASTNodeLabelOfFirstMatchingSubclause(): List<SimpleEntry<String, Match>> {
        val astLabel = astLabelForLabeledSubclause(memoKey, firstMatchingSubClauseIdx)
        val entry = SimpleEntry<String, Match>(astLabel, subClauseMatches[0])
        return listOf(entry)
    }

    private fun getLabeledSubclauseMatches(): List<SimpleEntry<String?, Match>> {
        return memoKey.clause.labeledSubClauses.indices
                .asSequence()
                .asStream()
                .map { index -> Pair(index, subClauseMatches[index]!!)}
                .map { (index, subClauseMatch) -> entryFor(subClauseMatch, memoKey, index) }
                .toList<SimpleEntry<String?, Match>>()
    }

    private fun entryFor(subClauseMatch: Match, key: MemoKey, index: Int): SimpleEntry<String?, Match> {
        val astNodeLabel = astLabelForLabeledSubclause(key, index)
        return SimpleEntry<String?, Match>(astNodeLabel, subClauseMatch)
    }

    private fun astLabelForLabeledSubclause(key: MemoKey, index: Int): String? {
        val labeledClause = key.clause.labeledSubClauses[index]
        return labeledClause?.astNodeLabel ?: null
    }

    /**
     * Compare this [Match] to another [Match] of the same [Clause] type and start position.
     *
     * @return true if this [Match] is a better match than the other [Match].
     */
    fun isBetterThan(other: Match): Boolean {
        return if (other === this) {
            false
        } else (memoKey.clause is First //
                && this.firstMatchingSubClauseIdx < other.firstMatchingSubClauseIdx) //
                || this.length > other.length
        // An earlier subclause match in a First clause is better than a later subclause match
        // A longer match (i.e. a match that spans more characters in the input) is better than a shorter match
    }

    fun toStringWithRuleNames(): String {
        val buf = StringBuilder()
        buf.append(memoKey.toStringWithRuleNames() + "+" + length)

        //        buf.append(memoKey.toStringWithRuleNames() + "+" + length + " => [ ");
        //        val subClauseMatchesToUse = getSubClauseMatches()
        //
        //        for (subClauseMatchEnt in subClauseMatchesToUse) {
        //            var subClauseMatch = subClauseMatchEnt.value;
        //            if (subClauseMatchIdx > 0) {
        //                buf.append(" ; ")
        //            }
        //            buf.append(subClauseMatch.toStringWithRuleNames())
        //        }
        //        buf.append(" ]")

        return buf.toString()
    }

    override fun toString(): String {
        val buf = StringBuilder()
        buf.append("${memoKey} + ${length}")

        //        buf.append(" => [ ")
        //        val subClauseMatchesToUse = getSubClauseMatches()
        //        for (s in subClauseMatchesToUse) {
        //            if (index > 0) {
        //                buf.append(" ; ");
        //            }
        //            buf.append(s.toString());
        //        }
        //        buf.append(" ]");

        return buf.toString()
    }

    companion object {

        /** There are no subclause matches for terminals.  */
        val NO_SUBCLAUSE_MATCHES = emptyArray<Match?>()
    }
}
