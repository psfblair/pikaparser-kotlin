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

/** The NotFollowedBy (inverted lookahead) PEG operator.  */
class NotFollowedBy(subClause: Clause) : Clause(subClause) {

    override fun determineWhetherCanMatchZeroChars() {
        // Set canMatchZeroChars to true
        canMatchZeroChars = true
        require(!labeledSubClauses[0].clause.canMatchZeroChars)
            { "Subclause always matches zero characters, so this clause will never match anything: ${this}" }
    }

    override fun match(memoTable: MemoTable, memoKey: MemoKey, input: String): Match? {
        val labeledSubClause = labeledSubClauses[0].clause
        val subClauseMemoKey = MemoKey(labeledSubClause, memoKey.startPos)
        val subClauseMatch = memoTable.lookUpBestMatch(subClauseMemoKey)
        return if (subClauseMatch == null) {
            // If there is no valid subclause match, return a new zero-length match
            Match(memoKey)
        } else {
            null
        }
    }

    override fun toString(): String {
        return updateStringCacheIfNecessary {
            "!${labeledSubClauses[0].toStringWithASTNodeLabel(parentClause = this)}"
        }
    }
}
