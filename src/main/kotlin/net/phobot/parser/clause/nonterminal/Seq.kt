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

package net.phobot.parser.clause.nonterminal

import net.phobot.parser.clause.Clause
import net.phobot.parser.memotable.Match
import net.phobot.parser.memotable.MemoKey
import net.phobot.parser.memotable.MemoTable
import java.util.*
import java.util.stream.Collectors
import kotlin.streams.asStream

/** The Seq (sequence) PEG operator.  */
class Seq(subClauses: Array<Clause>) : Clause(subClauses) {
    init {
        require(subClauses.size >= 2) { "${Seq::class.simpleName} expects 2 or more subclauses" }
    }

    override fun determineWhetherCanMatchZeroChars() {
        // For Seq, all subclauses must be able to match zero characters for the whole clause to
        // be able to match zero characters
        canMatchZeroChars = true
        for (element in labeledSubClauses) {
            if (!element.clause.canMatchZeroChars) {
                canMatchZeroChars = false
                break
            }
        }
    }

    override fun addAsSeedParentClause() {
        // All sub-clauses up to and including the first clause that matches one or more characters
        // needs to seed its parent clause if there is a subclause match
        val added = HashSet<Any>()
        for (element in labeledSubClauses) {
            val subClause = element.clause
            // Don't duplicate seed parent clauses in the subclause
            if (added.add(subClause)) {
                subClause.seedParentClauses.add(this)
            }
            if (!subClause.canMatchZeroChars) {
                // Don't need to any subsequent subclauses to seed this parent clause
                break
            }
        }
    }

    override fun match(memoTable: MemoTable, memoKey: MemoKey, input: String): Match? {
        val subClauseMatches: Array<Match?> = arrayOfNulls(labeledSubClauses.size)

        var currStartPos = memoKey.startPos
        for (subClauseIdx in labeledSubClauses.indices) {
            val subClause = labeledSubClauses[subClauseIdx].clause
            val subClauseMemoKey = MemoKey(subClause, currStartPos)
            val subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey)
                    ?: // Fail after first subclause fails to match
                    return null

            subClauseMatches[subClauseIdx] = subClauseMatch
            currStartPos += subClauseMatch.length
        }
        // All subclauses matched, so the Seq clause matches
        val length = currStartPos - memoKey.startPos
        return Match(memoKey, length, subClauseMatches)
    }

    override fun toString(): String {
        return updateStringCacheIfNecessary {
            labeledSubClauses
                    .asSequence()
                    .asStream()
                    .map { subClause -> subClause.toStringWithASTNodeLabel(parentClause = this) }
                    .collect(Collectors.joining(" "))
        }
    }
}
