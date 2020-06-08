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

package net.phobot.parser.utils

import net.phobot.parser.ast.ASTNode
import net.phobot.parser.grammar.GrammarPrecedenceLevels
import net.phobot.parser.memotable.Match
import kotlin.math.min

/** Tree utilities.  */
class TreeUtils {

    /** Print the parse tree rooted at a [Match] node to stdout.  */
    fun printTreeView(match: Match, input: String) {
        val buf = StringBuilder()
        renderTreeView(match, null, input, "", true, buf)
        println(buf.toString())
    }

    companion object {
        /** Render the AST rooted at an [ASTNode] into a StringBuffer.  */
        fun renderTreeView(astNode: ASTNode, input: String, indentStr: String, isLastChild: Boolean,
                           buf: StringBuilder) {
            val inpLen = 80
            var inp = input.substring(
                    astNode.startPos,
                    min(input.length, astNode.startPos + min(astNode.len, inpLen))
            )
            if (inp.length == inpLen) {
                inp += "..."
            }
            inp = StringUtils.escapeString(inp)

            // Uncomment for double-spaced rows
            //    buf.append("${indentStr}│\n")

            buf.append(indentStr + (if (isLastChild) "└─" else "├─") + astNode.label + " : " + astNode.startPos + "+"
                    + astNode.len + " : \"" + inp + "\"\n")
            if (astNode.children.isNotEmpty()) {
                for (i in 0 until astNode.children.size) {
                    renderTreeView(
                            astNode.children[i],
                            input,
                            indentStr = indentStr + if (isLastChild) "  " else "│ ",
                            isLastChild =  i == astNode.children.size - 1,
                            buf = buf
                    )
                }
            }
        }

        /** Render a parse tree rooted at a [Match] node into a StringBuffer.  */
        fun renderTreeView(match: Match, astNodeLabel: String?, input: String, indentStr: String,
                           isLastChild: Boolean, buf: StringBuilder) {
            val inpLen = 80
            var inp = input.substring(
                    match.memoKey.startPos,
                    min(input.length, match.memoKey.startPos + min(match.length, inpLen))
            )
            if (inp.length == inpLen) {
                inp += "..."
            }
            inp = StringUtils.escapeString(inp)

            // Uncomment for double-spaced rows
            // buf.append(indentStr + "│\n");

            val astNodeLabelNeedsParens = GrammarPrecedenceLevels.needToAddParensAroundASTNodeLabel(match.memoKey.clause)
            buf.append(indentStr)
            buf.append(if (isLastChild) "└─" else "├─")
            val ruleNames = match.memoKey.clause.ruleNames
            if (ruleNames.isNotEmpty()) {
                buf.append("${ruleNames} <- ")
            }
            if (astNodeLabel != null) {
                buf.append(astNodeLabel)
                buf.append(':')
                if (astNodeLabelNeedsParens) {
                    buf.append('(')
                }
            }
            val toStr = match.memoKey.clause.toString()
            buf.append(toStr)
            if (astNodeLabel != null && astNodeLabelNeedsParens) {
                buf.append(')')
            }
            buf.append(" : ")
            buf.append(match.memoKey.startPos)
            buf.append('+')
            buf.append(match.length)
            buf.append(" : \"")
            buf.append(inp)
            buf.append("\"\n")

            // Recurse to descendants
            val subClauseMatchesToUse = match.getSubClauseMatches()
            for (subClauseMatchIdx in subClauseMatchesToUse.indices) {
                val subClauseMatchEnt = subClauseMatchesToUse[subClauseMatchIdx]
                val subClauseASTNodeLabel = subClauseMatchEnt.key
                val subClauseMatch = subClauseMatchEnt.value
                renderTreeView(
                        subClauseMatch,
                        subClauseASTNodeLabel,
                        input,
                        indentStr = indentStr + if (isLastChild) "  " else "│ ",
                        isLastChild = subClauseMatchIdx == subClauseMatchesToUse.size - 1,
                        buf = buf
                )
            }
        }
    }
}
