/*
 * //
 * // This file is part of the pika parser implementation allowing whitespace-sensitive syntax. It is based
 * // on the Java reference implementation at:
 * //
 * //     https://github.com/lukehutch/pikaparser
 * //
 * // The pika parsing algorithm is described in the following paper:
 * //
 * //     Pika parsing: parsing in reverse solves the left recursion and error recovery problems
 * //     Luke A. D. Hutchison, May 2020
 * //     https://arxiv.org/abs/2005.06444
 * //
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
import net.phobot.parser.clause.nonterminal.NotFollowedBy
import net.phobot.parser.grammar.Grammar
import net.phobot.parser.utils.IntervalUnion
import java.util.*
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.Map.Entry

/** A memo entry for a specific [Clause] at a specific start position.  */
class MemoTable
// -------------------------------------------------------------------------------------------------------------

(
        /** The grammar.  */
        var grammar: Grammar,
        /** The input string.  */
        var input: String) {
    /**
     * A map from clause to startPos to a [Match] for the memo entry. (Use concurrent data structures so that
     * terminals can be memoized in parallel during initialization.)
     */
    private val memoTable = HashMap<MemoKey, Match>()

    // -------------------------------------------------------------------------------------------------------------

    /** The number of [Match] instances created.  */
    val numMatchObjectsCreated = AtomicInteger()

    /**
     * The number of [Match] instances added to the memo table (some will be overwritten by later matches).
     */
    val numMatchObjectsMemoized = AtomicInteger()

    // -------------------------------------------------------------------------------------------------------------

    /** Get all [Match] entries, indexed by clause then start position.  */
    private val allNavigableMatches: Map<Clause, NavigableMap<Int, Match>>
        get() {
            val clauseMap = HashMap<Clause, NavigableMap<Int, Match>>()
            memoTable.values.stream().forEach { match ->
                var startPosMap = clauseMap[match.memoKey.clause]
                if (startPosMap == null) {
                    startPosMap = TreeMap<Int, Match>()
                    clauseMap[match.memoKey.clause] = startPosMap
                }
                startPosMap[match.memoKey.startPos] = match
            }
            return clauseMap
        }

    /** Get all nonoverlapping [Match] entries, indexed by clause then start position.  */
    val allNonOverlappingMatches: Map<Clause, NavigableMap<Int, Match>>
        get() {
            val nonOverlappingClauseMap = HashMap<Clause, NavigableMap<Int, Match>>()
            for (ent in allNavigableMatches.entries) {
                val clause = ent.key
                val startPosMap = ent.value
                var prevEndPos = 0
                val nonOverlappingStartPosMap = TreeMap<Int, Match>()
                for (startPosEnt in startPosMap.entries) {
                    val startPos = startPosEnt.key
                    if (startPos >= prevEndPos) {
                        val match = startPosEnt.value
                        nonOverlappingStartPosMap[startPos] = match
                        val endPos = startPos + match.length
                        prevEndPos = endPos
                    }
                }
                nonOverlappingClauseMap[clause] = nonOverlappingStartPosMap
            }
            return nonOverlappingClauseMap
        }

    // -------------------------------------------------------------------------------------------------------------

    /** Look up the current best match for a given [MemoKey] in the memo table.  */
    fun lookUpBestMatch(memoKey: MemoKey): Match? {
        // Find current best match in memo table (null if there is no current best match)
        val bestMatch = memoTable[memoKey]
        return when {
            bestMatch != null -> // If there is a current best match, return it
                bestMatch
            memoKey.clause is NotFollowedBy -> // Need to match NotFollowedBy top-down
                memoKey.clause.match(this, memoKey, input)
            memoKey.clause.canMatchZeroChars -> // If there is no match in the memo table for this clause, but this clause can match zero characters,
                // then we need to return a new zero-length match to the parent clause. (This is part of the strategy
                // for minimizing the number of zero-length matches that are memoized.)
                // (N.B. this match will not have any subclause matches, which may be unexpected, so conversion of
                // parse tree to AST should be robust to this.)
                Match(memoKey)
            // No match was found in the memo table
            else -> null
        }
    }

    /**
     * Add a new [Match] to the memo table, if the match is non-null. Schedule seed parent clauses for
     * matching if the match is non-null or if the parent clause can match zero characters.
     */
    fun addMatch(memoKey: MemoKey, newMatch: Match?, priorityQueue: PriorityQueue<Clause>) {
        var matchUpdated = false
        if (newMatch != null) {
            // Track memoization
            numMatchObjectsCreated.incrementAndGet()

            // Get the memo entry for memoKey if already present; if not, create a new entry
            val oldMatch = memoTable[memoKey]

            // If there is no old match, or the new match is better than the old match
            if (oldMatch == null || newMatch.isBetterThan(oldMatch)) {
                // Store the new match in the memo entry
                memoTable[memoKey] = newMatch
                matchUpdated = true

                // Track memoization
                numMatchObjectsMemoized.incrementAndGet()
                if (Grammar.DEBUG) {
                    println("Setting new best match: ${newMatch.toStringWithRuleNames()}")
                }
            }
        }
        var i = 0
        val ii = memoKey.clause.seedParentClauses.size
        while (i < ii) {
            val seedParentClause = memoKey.clause.seedParentClauses[i]
            // If there was a valid match, or if there was no match but the parent clause can match
            // zero characters, schedule the parent clause for matching. (This is part of the strategy
            // for minimizing the number of zero-length matches that are memoized.)
            if (matchUpdated || seedParentClause.canMatchZeroChars) {
                priorityQueue.add(seedParentClause)
                if (Grammar.DEBUG) {
                    println("    Following seed parent clause: ${seedParentClause.toStringWithRuleNames()}")
                }
            }
            i++
        }
        if (Grammar.DEBUG) {
            println(
                    if (newMatch != null) "Matched: " else "Failed to match: ${memoKey.toStringWithRuleNames()}"
            )
        }
    }

    /** Get all [Match] entries for the given clause, indexed by start position.  */
    fun getNavigableMatches(clause: Clause): NavigableMap<Int, Match> {
        val treeMap = TreeMap<Int, Match>()
        memoTable.entries.stream().forEach { ent ->
            if (ent.key.clause === clause) {
                treeMap[ent.key.startPos] = ent.value
            }
        }
        return treeMap
    }

    /** Get the [Match] entries for all matches of this clause.  */
    fun getAllMatches(clause: Clause): List<Match> {
        val matches = ArrayList<Match>()
        getNavigableMatches(clause).entries.stream().forEach { ent -> matches.add(ent.value) }
        return matches
    }

    /**
     * Get the [Match] entries for all nonoverlapping matches of this clause, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    fun getNonOverlappingMatches(clause: Clause): List<Match> {
        val matches = getAllMatches(clause)
        val nonoverlappingMatches = ArrayList<Match>()
        var i = 0
        while (i < matches.size) {
            val match = matches[i]
            val startPos = match.memoKey.startPos
            val endPos = startPos + match.length
            nonoverlappingMatches.add(match)
            while (i < matches.size - 1 && matches[i + 1].memoKey.startPos < endPos) {
                i++
            }
            i++
        }
        return nonoverlappingMatches
    }

    /**
     * Get any syntax errors in the parse, as a map from start position to a tuple, (end position, span of input
     * string between start position and end position).
     */
    fun getSyntaxErrors(vararg syntaxCoverageRuleNames: String): NavigableMap<Int, Entry<Int, String>> {
        // Find the range of characters spanned by matches for each of the coverageRuleNames
        val parsedRanges = IntervalUnion()
        for (coverageRuleName in syntaxCoverageRuleNames) {
            val rule = grammar.getRule(coverageRuleName)
            for (match in getNonOverlappingMatches(rule.labeledClause.clause)) {
                parsedRanges.addRange(match.memoKey.startPos, match.memoKey.startPos + match.length)
            }
        }
        // Find the inverse of the parsed ranges -- these are the syntax errors
        val unparsedRanges = parsedRanges.invert(0, input.length).nonOverlappingRanges
        // Extract the input string span for each unparsed range
        val syntaxErrorSpans = TreeMap<Int, Entry<Int, String>>()
        unparsedRanges.entries.stream().forEach { ent ->
            val entry = SimpleEntry<Int, String>(ent.value, input.substring(ent.key, ent.value))
            syntaxErrorSpans[ent.key] = entry
        }
        return syntaxErrorSpans
    }
}
