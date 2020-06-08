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
import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.nonterminal.Seq
import net.phobot.parser.clause.terminal.Terminal
import net.phobot.parser.grammar.Grammar
import net.phobot.parser.memotable.Match
import net.phobot.parser.memotable.MemoKey
import net.phobot.parser.memotable.MemoTable
import java.util.*
import kotlin.collections.Map.Entry
import kotlin.math.max

/** Utility methods for printing information about the result of a parse.  */
object ParserInfo {
    /** Print all the clauses in a grammar.  */
    private fun printClauses(grammar: Grammar) {
        for (i in grammar.allClauses.size - 1 downTo 0) {
            val clause = grammar.allClauses[i]
            println(String.format("%3d : %s", i, clause.toStringWithRuleNames()))
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Print the memo table.  */
    private fun printMemoTable(memoTable: MemoTable) {
        val buf = arrayOfNulls<StringBuilder>(memoTable.grammar.allClauses.size)
        var marginWidth = 0
        for (i in memoTable.grammar.allClauses.indices) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(String.format("%3d", memoTable.grammar.allClauses.size - 1 - i) + " : ")
            val clause = memoTable.grammar.allClauses[memoTable.grammar.allClauses.size - 1 - i]
            if (clause is Terminal) {
                stringBuilder.append("[terminal] ")
            }
            if (clause.canMatchZeroChars) {
                stringBuilder.append("[canMatchZeroChars] ")
            }
            stringBuilder.append(clause.toStringWithRuleNames())
            marginWidth = max(marginWidth, stringBuilder.length + 2)

            buf[i] = stringBuilder
        }

        val tableWidth = marginWidth + memoTable.input.length + 1
        for (i in memoTable.grammar.allClauses.indices) {
            val stringBuilder = buf[i] ?: StringBuilder()
            while (stringBuilder.length < marginWidth) {
                stringBuilder.append(' ')
            }
            while (stringBuilder.length < tableWidth) {
                stringBuilder.append('-')
            }
            buf[i] = stringBuilder
        }

        val nonOverlappingMatches = memoTable.allNonOverlappingMatches
        for (clauseIdx in memoTable.grammar.allClauses.size - 1 downTo 0) {
            val row = memoTable.grammar.allClauses.size - 1 - clauseIdx
            val stringBuilder = buf[row] ?: StringBuilder()

            val clause = memoTable.grammar.allClauses[clauseIdx]
            val matchesForClause = nonOverlappingMatches[clause]
            if (matchesForClause != null) {
                for (matchEnt in matchesForClause.entries) {
                    val match = matchEnt.value
                    val matchStartPos = match.memoKey.startPos
                    val matchEndPos = matchStartPos + match.length
                    if (matchStartPos <= memoTable.input.length) {
                        stringBuilder.setCharAt(marginWidth + matchStartPos, '#')
                        for (j in matchStartPos + 1 until matchEndPos) {
                            if (j <= memoTable.input.length) {
                                stringBuilder.setCharAt(marginWidth + j, '=')
                            }
                        }
                    }
                }
            }
            println(stringBuilder)
            buf[row] = stringBuilder
        }

        for (j in 0 until marginWidth) {
            print(' ')
        }

        for (i in memoTable.input.indices) {
            print(i % 10)
        }

        println()

        for (i in 0 until marginWidth) {
            print(' ')
        }

        println(StringUtils.replaceNonASCII(memoTable.input))
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Find the cycle depth of a given match (the maximum number of grammar cycles in any path between the match and
     * any descendant terminal match).
     */
    private fun findCycleDepth(match: Match,
                               cycleDepthToMatches: MutableMap<Int, MutableMap<Int, MutableMap<Int, Match>>>): Int {
        var cycleDepth = 0
        for (subClauseMatchEnt in match.getSubClauseMatches()) {
            val subClauseMatch = subClauseMatchEnt.value
            val subClauseIsInDifferentCycle = //
                    match.memoKey.clause.clauseIdx <= subClauseMatch.memoKey.clause.clauseIdx
            val subClauseMatchDepth = findCycleDepth(subClauseMatch, cycleDepthToMatches)
            cycleDepth = cycleDepth.coerceAtLeast(if (subClauseIsInDifferentCycle) subClauseMatchDepth + 1 else subClauseMatchDepth)
        }

        var matchesForDepth = cycleDepthToMatches[cycleDepth]
        if (matchesForDepth == null) {
            matchesForDepth = TreeMap(Collections.reverseOrder())
            cycleDepthToMatches[cycleDepth] = matchesForDepth
        }

        var matchesForClauseIdx = matchesForDepth[match.memoKey.clause.clauseIdx]
        if (matchesForClauseIdx == null) {
            matchesForClauseIdx = TreeMap()
            matchesForDepth[match.memoKey.clause.clauseIdx] = matchesForClauseIdx
        }

        matchesForClauseIdx[match.memoKey.startPos] = match
        return cycleDepth
    }

    /** Print the parse tree in memo table form.  */
    private fun printParseTreeInMemoTableForm(memoTable: MemoTable) {
        require(memoTable.grammar.allClauses.isNotEmpty()) { "Grammar is empty" }

        // Map from cycle depth (sorted in decreasing order) -> clauseIdx -> startPos -> match
        val cycleDepthToMatches = TreeMap<Int, MutableMap<Int, MutableMap<Int, Match>>>(
                Collections.reverseOrder())

        // Input spanned by matches found so far
        val inputSpanned = IntervalUnion()

        // Get all nonoverlapping matches rules, top-down.
        val nonOverlappingMatches = memoTable.allNonOverlappingMatches
        var maxCycleDepth = 0
        for (clauseIdx in memoTable.grammar.allClauses.size - 1 downTo 0) {
            val clause = memoTable.grammar.allClauses[clauseIdx]
            val matchesForClause = nonOverlappingMatches[clause]
            if (matchesForClause != null) {
                for (matchEnt in matchesForClause.entries) {
                    val match = matchEnt.value
                    val matchStartPos = match.memoKey.startPos
                    val matchEndPos = matchStartPos + match.length
                    // Only add parse tree to chart if it doesn't overlap with input spanned by a higher-level match
                    if (!inputSpanned.rangeOverlaps(matchStartPos, matchEndPos)) {
                        // Pack matches into the lowest cycle they will fit into
                        val cycleDepth = findCycleDepth(match, cycleDepthToMatches)
                        maxCycleDepth = max(maxCycleDepth, cycleDepth)
                        // Add the range spanned by this match
                        inputSpanned.addRange(matchStartPos, matchEndPos)
                    }
                }
            }
        }

        // Assign matches to rows
        val matchesForRow = ArrayList<Map<Int, Match>>()
        val clauseForRow = ArrayList<Clause>()
        for (matchesForDepth in cycleDepthToMatches.values) {
            for (matchesForClauseIdxEnt in matchesForDepth.entries) {
                clauseForRow.add(memoTable.grammar.allClauses[matchesForClauseIdxEnt.key])
                matchesForRow.add(matchesForClauseIdxEnt.value)
            }
        }

        // Set up row labels
        val rowLabel = arrayOfNulls<StringBuilder>(clauseForRow.size)
        var rowLabelMaxWidth = 0

        for (i in clauseForRow.indices) {
            val rowLabelBuilder = StringBuilder()
            val clause = clauseForRow[i]

            if (clause is Terminal) {
                rowLabelBuilder.append("[terminal] ")
            }
            if (clause.canMatchZeroChars) {
                rowLabelBuilder.append("[canMatchZeroChars] ")
            }
            rowLabelBuilder.append(clause.toStringWithRuleNames())
            rowLabelBuilder.append("  ")

            rowLabelMaxWidth = rowLabelMaxWidth.coerceAtLeast(rowLabelBuilder.length)
            rowLabel[i] = rowLabelBuilder
        }

        for (i in clauseForRow.indices) {
            val label = rowLabel[i]?.toString() ?: ""
            val rowLabelBuilder = rowLabel[i] ?: StringBuilder()

            val clause = clauseForRow[i]
            val clauseIdx = clause.clauseIdx

            // Right-justify the row label
            rowLabelBuilder.setLength(0)
            var j = 0
            val jj = rowLabelMaxWidth - label.length
            while (j < jj) {
                rowLabelBuilder.append(' ')
                j++
            }
            rowLabelBuilder.append(String.format("%3d", clauseIdx) + " : ")
            rowLabelBuilder.append(label)

            rowLabel[i] = rowLabelBuilder
        }

        val emptyRowLabel = StringBuilder()
        run {
            var i = 0
            val ii = rowLabelMaxWidth + 6
            while (i < ii) {
                emptyRowLabel.append(' ')
                i++
            }
        }

        val edgeMarkers = StringBuilder()
        edgeMarkers.append(' ')
        run {
            var i = 1
            val ii = memoTable.input.length * 2
            while (i < ii) {
                edgeMarkers.append('░')
                i++
            }
        }

        // Append one char for last column boundary, and two extra chars for zero-length matches past end of string
        edgeMarkers.append("   ")

        // Add tree structure to right of row label
        for (row in clauseForRow.indices) {
            val matches = matchesForRow[row]

            val rowTreeChars = StringBuilder()
            rowTreeChars.append(edgeMarkers)
            val zeroLenMatchIdxs = ArrayList<Int>()
            for (ent in matches.entries) {
                val match = ent.value
                val startIdx = match.memoKey.startPos
                val endIdx = startIdx + match.length

                if (startIdx == endIdx) {
                    // Zero-length match
                    zeroLenMatchIdxs.add(startIdx)
                } else {
                    // Match consumes 1 or more characters
                    for (i in startIdx..endIdx) {
                        val chrLeft = rowTreeChars[i * 2]
                        rowTreeChars.setCharAt(i * 2,
                                if (i == startIdx)
                                    if (chrLeft == '│') '├' else if (chrLeft == '┤') '┼' else if (chrLeft == '┐') '┬' else '┌'
                                else if (i == endIdx) if (chrLeft == '│') '┤' else '┐' else '─')
                        if (i < endIdx) {
                            rowTreeChars.setCharAt(i * 2 + 1, '─')
                        }
                    }
                }
            }
            print(emptyRowLabel)
            println(rowTreeChars)

            for (ent in matches.entries) {
                val match = ent.value
                val startIdx = match.memoKey.startPos
                val endIdx = startIdx + match.length
                edgeMarkers.setCharAt(startIdx * 2, '│')
                edgeMarkers.setCharAt(endIdx * 2, '│')
                var i = startIdx * 2 + 1
                val ii = endIdx * 2

                while (i < ii) {
                    val c = edgeMarkers[i]
                    if (c == '░' || c == '│') {
                        edgeMarkers.setCharAt(i, ' ')
                    }
                    i++
                }
            }
            rowTreeChars.setLength(0)
            rowTreeChars.append(edgeMarkers)
            for (ent in matches.entries) {
                val match = ent.value
                val startIdx = match.memoKey.startPos
                val endIdx = startIdx + match.length
                for (i in startIdx until endIdx) {
                    rowTreeChars.setCharAt(i * 2 + 1, StringUtils.replaceNonASCII(memoTable.input[i]))
                }
            }
            for (zeroLenMatchIdx in zeroLenMatchIdxs) {
                rowTreeChars.setCharAt(zeroLenMatchIdx * 2, '▮')
            }
            print(rowLabel[row])
            println(rowTreeChars)
        }

        // Print input index digits
        for (j in 0 until rowLabelMaxWidth + 6) {
            print(' ')
        }
        print(' ')
        for (i in memoTable.input.indices) {
            print(i % 10)
            print(' ')
        }
        println()

        // Print input string
        for (i in 0 until rowLabelMaxWidth + 6) {
            print(' ')
        }
        print(' ')
        for (element in memoTable.input) {
            print(StringUtils.replaceNonASCII(element))
            print(' ')
        }
        println()
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Print syntax errors obtained from [MemoTable.getSyntaxErrors] via [net.phobot.parser.grammar.MetaGrammar.parse].  */
    fun printSyntaxErrors(syntaxErrors: NavigableMap<Int, Entry<Int, String>>) {
        if (!syntaxErrors.isEmpty()) {
            println("\nSYNTAX ERRORS:\n")
            for (ent in syntaxErrors.entries) {
                val startPos = ent.key
                val endPos = ent.value.key
                val syntaxErrStr = ent.value.value
                val length = endPos - startPos
                val syntaxErrorStr = StringUtils.replaceNonASCII(syntaxErrStr)
                // TODO: show line numbers
                println("${startPos}+${length} : ${syntaxErrorStr}")
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Print matches in the memo table for a given clause.  */
    private fun printMatches(clause: Clause, memoTable: MemoTable, showAllMatches: Boolean) {
        val matches = memoTable.getAllMatches(clause)
        val clauseString = clause.toStringWithRuleNames()

        if (matches.isNotEmpty()) {
            println("\n====================================\n\nMatches for ${clauseString} :")
            // Get toplevel AST node label(s), if present
            var astNodeLabel = ""
            for (rule in clause.rules) {
                if (rule.labeledClause.astNodeLabel != null) {
                    if (astNodeLabel.isNotEmpty()) {
                        astNodeLabel += ":"
                    }
                    astNodeLabel += rule.labeledClause.astNodeLabel
                }
            }
            var prevEndPos = -1
            for (j in matches.indices) {
                val match = matches[j]
                // Indent matches that overlap with previous longest match
                val overlapsPrevMatch = match.memoKey.startPos < prevEndPos
                if (!overlapsPrevMatch || showAllMatches) {
                    val indent = if (overlapsPrevMatch) "    " else ""
                    val buf = StringBuilder()
                    TreeUtils.renderTreeView(match, if (astNodeLabel.isEmpty()) null else astNodeLabel, memoTable.input,
                            indent, true, buf)
                    println(buf.toString())
                }
                val newEndPos = match.memoKey.startPos + match.length
                if (newEndPos > prevEndPos) {
                    prevEndPos = newEndPos
                }
            }
        } else {
            println("\n====================================\n\nNo matches for ${clauseString}")
        }
    }

    /** Print matches in the memo table for a given clause and its subclauses.  */
    private fun printMatchesAndSubClauseMatches(clause: Clause, memoTable: MemoTable) {
        printMatches(clause, memoTable, showAllMatches = true)
        for (element in clause.labeledSubClauses) {
            printMatches(element.clause, memoTable, showAllMatches = true)
        }
    }

    /**
     * Print matches in the memo table for a given Seq clause and its subclauses, including partial matches of the
     * Seq.
     */
    private fun printMatchesAndPartialMatches(seqClause: Seq, memoTable: MemoTable) {
        val numSubClauses = seqClause.labeledSubClauses.size
        for (subClause0Match in memoTable.getAllMatches(seqClause.labeledSubClauses[0].clause)) {
            val subClauseMatches = ArrayList<Match>()
            subClauseMatches.add(subClause0Match)
            val currStartPos = subClause0Match.memoKey.startPos + subClause0Match.length
            for (i in 1 until numSubClauses) {
                val subClauseIMatch = memoTable
                        .lookUpBestMatch(MemoKey(seqClause.labeledSubClauses[i].clause, currStartPos)) ?: break
                subClauseMatches.add(subClauseIMatch)
            }
            println(  "\n====================================\n\nMatched "
                    + (if (subClauseMatches.size == numSubClauses) "all subclauses"
                       else "${subClauseMatches.size} out of ${numSubClauses} subclauses"
                    )
                    + " of clause (${seqClause}) at start pos ${subClause0Match.memoKey.startPos}"
                    )
            println()
            for (i in 0 until subClauseMatches.size) {
                val subClauseMatch = subClauseMatches[i]
                val buf = StringBuilder()
                TreeUtils.renderTreeView(
                        subClauseMatch,
                        seqClause.labeledSubClauses[i].astNodeLabel,
                        memoTable.input,
                        indentStr = "",
                        isLastChild = true,
                        buf = buf
                )
                println(buf.toString())
            }
        }
    }

    /** Print the AST for a given clause.  */
    private fun printAST(astNodeLabel: String, clause: Clause, memoTable: MemoTable) {
        val matches = memoTable.getNonOverlappingMatches(clause)
        for (i in matches.indices) {
            val match = matches[i]
            val ast = ASTNode(astNodeLabel, match, memoTable.input)
            println(ast.toString())
        }
    }

    /** Summarize a parsing result.  */
    fun printParseResult(topLevelRuleName: String, memoTable: MemoTable,
                         syntaxCoverageRuleNames: Array<String>, showAllMatches: Boolean) {
        println()
        println("Clauses:")
        printClauses(memoTable.grammar)

        println()
        println("Memo Table:")
        printMemoTable(memoTable)

        // Print memo table
        println()
        println("Match tree for rule $topLevelRuleName:")
        printParseTreeInMemoTableForm(memoTable)

        // Print all matches for each clause
        for (i in memoTable.grammar.allClauses.size - 1 downTo 0) {
            val clause = memoTable.grammar.allClauses[i]
            printMatches(clause, memoTable, showAllMatches)
        }

        val rule = memoTable.grammar.ruleNameWithPrecedenceToRule[topLevelRuleName]
        if (rule != null) {
            println(
                    "\n====================================\n\nAST for rule \"$topLevelRuleName\":\n")
            val ruleClause = rule.labeledClause.clause
            printAST(topLevelRuleName, ruleClause, memoTable)
        } else {
            println("\nRule \"$topLevelRuleName\" does not exist")
        }

        val syntaxErrors = memoTable.getSyntaxErrors(*syntaxCoverageRuleNames)
        if (syntaxErrors.isNotEmpty()) {
            printSyntaxErrors(syntaxErrors)
        }

        println("\nNum match objects created: " + memoTable.numMatchObjectsCreated)
        println("Num match objects memoized:  " + memoTable.numMatchObjectsMemoized)
    }
}
