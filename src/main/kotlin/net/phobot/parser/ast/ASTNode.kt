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

package net.phobot.parser.ast

import net.phobot.parser.clause.Clause
import net.phobot.parser.memotable.Match
import net.phobot.parser.utils.TreeUtils

// AST node names:

const val RULE_AST = "RuleAST"
const val PREC_AST = "PrecAST"
const val R_ASSOC_AST = "RAssocAST"
const val L_ASSOC_AST = "LAssocAST"
const val IDENT_AST = "IdentAST"
const val LABEL_AST = "LabelAST"
const val LABEL_NAME_AST = "LabelNameAST"
const val LABEL_CLAUSE_AST = "LabelClauseAST"
const val SEQ_AST = "SeqAST"
const val FIRST_AST = "FirstAST"
const val FOLLOWED_BY_AST = "FollowedByAST"
const val NOT_FOLLOWED_BY_AST = "NotFollowedByAST"
const val ONE_OR_MORE_AST = "OneOrMoreAST"
const val ZERO_OR_MORE_AST = "ZeroOrMoreAST"
const val OPTIONAL_AST = "OptionalAST"
const val SINGLE_QUOTED_CHAR_AST = "SingleQuotedCharAST"
const val CHAR_RANGE_AST = "CharRangeAST"
const val QUOTED_STRING_AST = "QuotedStringAST"
const val START_AST = "StartAST"
const val NOTHING_AST = "NothingAST";

/** A node in the Abstract Syntax Tree (AST).  */
class ASTNode private constructor(val label: String, val nodeType: Clause, val startPos: Int, val len: Int, val input: String) {
    val children: MutableList<ASTNode> = ArrayList()

    val onlyChild: ASTNode
        get() {
            require(children.size == 1) { "Expected one child, got ${children.size}" }
            return children[0]
        }

    val firstChild: ASTNode
        get() {
            require(children.size >= 1) { "No first child" }
            return children[0]
        }

    val secondChild: ASTNode
        get() {
            require(children.size >= 2) { "No second child" }
            return children[1]
        }

    val thirdChild: ASTNode
        get() {
            require(children.size >= 3) { "No third child" }
            return children[2]
        }

    val text: String
        get() = input.substring(startPos, startPos + len)

    /** Recursively create an AST from a parse tree.  */
    constructor(label: String, match: Match, input: String) : this(label, match.memoKey.clause, match.memoKey.startPos, match.length, input) {
        addNodesWithASTNodeLabelsRecursive(parentASTNode = this, parentMatch = match, input = input)
    }

    /** Recursively convert a match node to an AST node.  */
    private fun addNodesWithASTNodeLabelsRecursive(parentASTNode: ASTNode, parentMatch: Match, input: String) {
        // Recurse to descendants
        val subClauseMatchesToUse = parentMatch.getSubClauseMatches()
        for (subClauseMatchIdx in subClauseMatchesToUse.indices) {
            val subClauseMatchEnt = subClauseMatchesToUse[subClauseMatchIdx]
            val subClauseASTNodeLabel = subClauseMatchEnt.key
            val subClauseMatch = subClauseMatchEnt.value
            if (subClauseASTNodeLabel != null) {
                // Create an AST node for any labeled sub-clauses
                parentASTNode.children.add(ASTNode(subClauseASTNodeLabel, subClauseMatch, input))
            } else {
                // Do not add an AST node for parse tree nodes that are not labeled; however, still need
                // to recurse to their subclause matches
                addNodesWithASTNodeLabelsRecursive(parentASTNode, subClauseMatch, input)
            }
        }
    }

    private fun getAllDescendantsNamed(name: String, termsOut: MutableList<ASTNode>) {
        if (label == name) {
            termsOut.add(this)
        } else {
            for (child in children) {
                child.getAllDescendantsNamed(name, termsOut)
            }
        }
    }

    fun getAllDescendantsNamed(name: String): List<ASTNode> {
        val terms = ArrayList<ASTNode>()
        getAllDescendantsNamed(name, terms)
        return terms
    }

    fun getFirstDescendantNamed(name: String): ASTNode {
        if (label == name) {
            return this
        } else {
            for (child in children) {
                return child.getFirstDescendantNamed(name)
            }
        }
        throw IllegalArgumentException("Node not found: \"$name\"")
    }

    fun getChild(i: Int): ASTNode {
        return children[i]
    }

    override fun toString(): String {
        val buf = StringBuilder()
        TreeUtils.renderTreeView(astNode = this, input = input, indentStr = "", isLastChild = true, buf = buf)
        return buf.toString()
    }
}
