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

import net.phobot.parser.ast.ASTNode
import net.phobot.parser.ast.L_ASSOC_AST
import net.phobot.parser.ast.R_ASSOC_AST
import net.phobot.parser.clause.Clause

object RuleParser {

    /** Construct a [Rule].  */
    fun rule(ruleName: String, clause: Clause): Rule {
        // Use -1 as precedence if rule group has only one precedence
        return rule(ruleName, precedence = -1, associativity = null, clause = clause)
    }

    /** Construct a [Rule] with the given precedence and associativity.  */
    fun rule(ruleName: String, precedence: Int, associativity: Rule.Associativity?, clause: Clause): Rule {
        return Rule(ruleName, precedence, associativity, clause)
    }

    /** Parse a rule in the AST, returning a new [Rule].  */
    fun parseRule(ruleNode: ASTNode): Rule {
        val ruleName = ruleNode.firstChild.text
        val hasPrecedence = ruleNode.children.size > 2
        val associativity = when {
            ruleNode.children.size < 4 -> null
            ruleNode.thirdChild.label == L_ASSOC_AST -> Rule.Associativity.LEFT
            ruleNode.thirdChild.label == R_ASSOC_AST -> Rule.Associativity.RIGHT
            else -> null
        }
        val precedence = if (hasPrecedence) Integer.parseInt(ruleNode.secondChild.text) else -1

        require(!hasPrecedence || precedence >= 0)
        {
            ("If there is precedence, it must be zero or positive (rule ${ruleName} " +
                    "has precedence level ${precedence})")
        }

        val astNode = ruleNode.getChild(ruleNode.children.size - 1)
        val clause = MetaGrammarAstNodeParser.parseASTNode(astNode)
        return rule(ruleName, precedence, associativity, clause)
    }
}