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

package net.phobot.parser.grammar.utils

import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.terminal.Terminal
import net.phobot.parser.grammar.Rule
import java.util.ArrayList
import java.util.HashSet

/** Topologically sort all clauses into bottom-up order, from terminals up to the toplevel clause.  */
fun findClauseTopoSortOrder(allRules: List<Rule>, lowestPrecedenceClauses: List<Clause>): List<Clause> {
    // Find toplevel clauses (clauses that are not a subclause of any other clause)
    val allClausesUnordered = ArrayList<Clause>()
    val topLevelVisited = HashSet<Clause>()
    for (rule in allRules) {
        findReachableClauses(rule.labeledClause.clause, topLevelVisited, allClausesUnordered)
    }
    val topLevelClauses = HashSet<Clause>(allClausesUnordered)
    for (clause in allClausesUnordered) {
        for (labeledSubClause in clause.labeledSubClauses) {
            topLevelClauses.remove(labeledSubClause.clause)
        }
    }
    val topLevelClausesOrdered = ArrayList(topLevelClauses)

    // Add to the end of the list of toplevel clauses all lowest-precedence clauses, since
    // top-down precedence climbing should start at each lowest-precedence clause
    topLevelClausesOrdered.addAll(lowestPrecedenceClauses)

    // Finally, in case there are cycles in the grammar that are not part of a precedence
    // hierarchy, add to the end of the list of toplevel clauses the set of all "head clauses"
    // of cycles (the set of all clauses reached twice in some path through the grammar)
    val cycleDiscovered = HashSet<Clause>()
    val cycleFinished = HashSet<Clause>()
    val cycleHeadClauses = HashSet<Clause>()
    for (clause in topLevelClauses) {
        findCycleHeadClauses(clause, cycleDiscovered, cycleFinished, cycleHeadClauses)
    }
    for (rule in allRules) {
        findCycleHeadClauses(rule.labeledClause.clause, cycleDiscovered, cycleFinished, cycleHeadClauses)
    }
    topLevelClausesOrdered.addAll(cycleHeadClauses)

    // Topologically sort all clauses into bottom-up order, starting with terminals (so that terminals are
    // all grouped together at the beginning of the list)
    val terminalsVisited = HashSet<Clause>()
    val terminals = ArrayList<Clause>()
    for (rule in allRules) {
        findTerminals(rule.labeledClause.clause, terminalsVisited, terminals)
    }
    val allClauses = ArrayList<Clause>(terminals)
    val reachableVisited = HashSet<Clause>()
    reachableVisited.addAll(terminals)
    for (topLevelClause in topLevelClausesOrdered) {
        findReachableClauses(topLevelClause, reachableVisited, allClauses)
    }

    // Give each clause an index in the topological sort order, bottom-up
    for (i in 0 until allClauses.size) {
        allClauses[i].clauseIdx = i
    }
    return allClauses
}

/** Find the [Clause] nodes that complete a cycle in the grammar.  */
private fun findCycleHeadClauses(clause: Clause, discovered: MutableSet<Clause>, finished: MutableSet<Clause>,
                                 cycleHeadClausesOut: MutableSet<Clause>) {
    require(clause !is RuleRef)
    { "There should not be any ${RuleRef::class.simpleName} nodes left in grammar" }
    discovered.add(clause)
    for (labeledSubClause in clause.labeledSubClauses) {
        val subClause = labeledSubClause.clause
        if (discovered.contains(subClause)) {
            // Reached a cycle
            cycleHeadClausesOut.add(subClause)
        } else if (!finished.contains(subClause)) {
            findCycleHeadClauses(subClause, discovered, finished, cycleHeadClausesOut)
        }
    }
    discovered.remove(clause)
    finished.add(clause)
}

/** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position.  */
private fun findTerminals(clause: Clause, visited: HashSet<Clause>, terminalsOut: MutableList<Clause>) {
    if (visited.add(clause)) {
        if (clause is Terminal) {
            terminalsOut.add(clause)
        } else {
            for (labeledSubClause in clause.labeledSubClauses) {
                val subClause = labeledSubClause.clause
                findTerminals(subClause, visited, terminalsOut)
            }
        }
    }
}

/** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position.  */
private fun findReachableClauses(clause: Clause, visited: HashSet<Clause>, revTopoOrderOut: MutableList<Clause>) {
    if (visited.add(clause)) {
        for (labeledSubClause in clause.labeledSubClauses) {
            val subClause = labeledSubClause.clause
            findReachableClauses(subClause, visited, revTopoOrderOut)
        }
        revTopoOrderOut.add(clause)
    }
}
