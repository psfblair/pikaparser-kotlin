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

package net.phobot.parser.grammar

import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.aux.ASTNodeLabel
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.nonterminal.*
import net.phobot.parser.clause.terminal.Terminal


object GrammarPrecedenceLevels {

    // GrammarPrecedenceLevels levels (should correspond to levels in the grammar):
    val clauseTypeToPrecedence =
            mapOf(
               Terminal::class to      7,
               // Treat RuleRef as having the same precedence as a terminal for string interning purposes
               RuleRef::class to       7,
               OneOrMore::class to     6,
               // ZeroOrMore is not present in the final grammar, so it is skipped here
               NotFollowedBy::class to 5,
               FollowedBy::class to    5,
               // Optional is not present in final grammar, so it is skipped here
               ASTNodeLabel::class to  3,
               Seq::class to           2,
               First::class to         1
            )

    /**
     * Return true if subClause precedence is less than or equal to parentClause precedence (or if subclause is a
     * [Seq] clause and parentClause is a [First] clause, for clarity, even though parens are not needed
     * because Seq has higher prrecedence).
     */
    fun needToAddParensAroundSubClause(parentClause: Clause, subClause: Clause): Boolean {
        val clausePrec : Int =
                if (parentClause is Terminal)
                    clauseTypeToPrecedence[Terminal::class] ?: error("Precedence of Terminal has become undefined")
                else
                    clauseTypeToPrecedence[parentClause::class] ?: error("Precedence of ${parentClause::class.simpleName} is undefined")

        val subClausePrec : Int =
                if (subClause is Terminal)
                    clauseTypeToPrecedence[Terminal::class] ?: error("Precedence of Terminal has become undefined")
                else {
                    clauseTypeToPrecedence[subClause::class] ?: error("Precedence of ${subClause::class.simpleName} is undefined")
                }

                // Always parenthesize Seq inside First for clarity, even though Seq has higher precedence
        return (parentClause is First && subClause is Seq
                // Add parentheses around subclauses that are lower or equal precedence to parent clause
               || subClausePrec <= clausePrec)
    }

    /** Return true if subclause has lower precedence than an AST node label.  */
    fun needToAddParensAroundASTNodeLabel(subClause: Clause): Boolean {
        val astNodeLabelPrec : Int = clauseTypeToPrecedence[ASTNodeLabel::class] ?: error("Precedence of ASTNodeLabel has become undefined")
        val subClausePrec : Int =
                if (subClause is Terminal)
                    clauseTypeToPrecedence[Terminal::class] ?: error("Precedence of Terminal has become undefined")
                else
                    clauseTypeToPrecedence[subClause::class] ?: error("Precedence of ${subClause::class.simpleName} is undefined")

        return subClausePrec < astNodeLabelPrec
    }
}