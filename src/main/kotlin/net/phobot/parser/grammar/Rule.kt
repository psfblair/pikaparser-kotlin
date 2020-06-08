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

package net.phobot.parser.grammar

import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.aux.ASTNodeLabel
import net.phobot.parser.clause.aux.LabeledClause

/** A grammar rule.  */
class Rule
/** Construct a rule with specified precedence and associativity.  */
(
  /** The name of the rule.  */
  var ruleName: String,
  /** The precedence of the rule, or -1 for no specified precedence.  */
  val precedence: Int,
  /** The associativity of the rule, or null for no specified associativity.  */
  val associativity: Associativity?,

  clause: Clause) {

    /** The toplevel clause of the rule, and any associated AST node label.  */
    var labeledClause: LabeledClause

    /** Associativity (null implies no specified associativity).  */
    enum class Associativity {
        LEFT, RIGHT
    }

    init {
        var astNodeLabel: String? = null
        var clauseToUse = clause
        if (clause is ASTNodeLabel) {
            // Transfer ASTNodeLabel.astNodeLabel to astNodeLabel
            astNodeLabel = clause.astNodeLabel
            // skip over ASTNodeLabel node when adding subClause to subClauses array
            clauseToUse = clause.labeledSubClauses[0].clause
        }
        this.labeledClause = LabeledClause(clauseToUse, astNodeLabel)
    }

    /** Construct a rule with no specified precedence or associativity.  */
    constructor(ruleName: String, clause: Clause) : this(ruleName, -1, null, clause) {}
    // Use precedence of -1 for rules that only have one precedence
    // (this causes the precedence number not to be shown in the output of toStringWithRuleNames())

    override fun toString(): String {
        val buf = StringBuilder()
        buf.append(ruleName)
        buf.append(" <- ")
        buf.append(labeledClause.toString())
        return buf.toString()
    }
}
