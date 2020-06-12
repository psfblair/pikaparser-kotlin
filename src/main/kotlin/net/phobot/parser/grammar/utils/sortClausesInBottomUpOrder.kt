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
import cyclops.data.HashSet
import cyclops.data.Seq
import cyclops.data.Vector
import net.phobot.parser.clause.aux.LabeledClause

data class SortResult(val orderedClauses: Vector<Clause>, val topLevelClauses: Seq<Clause>, val terminals: Seq<Clause>)

/** Topologically sort all clauses into bottom-up order, from terminals up to the toplevel clause.  */
fun sortClausesInBottomUpOrder(allRules: List<Rule>, lowestPrecedenceClauses: List<Clause>): SortResult {

    // Find top-level clauses (clauses that are not a subclause of any other clause)
    val topLevelClausesOrdered = findTopLevelClauses( Seq.fromIterable(allRules)
                                                    , Seq.fromIterable(lowestPrecedenceClauses))

    // Topologically sort all clauses into bottom-up order, starting with terminals (so that terminals are
    // all grouped together at the beginning of the list)
    val allTerminals =
        allRules
            .map { rule -> rule.labeledClause.clause }
            .fold(OrderedClauseAccumulator(), ::findTerminals)
            .orderedClauses

    val accumulatorWithTerminalsVisited =
            OrderedClauseAccumulator(HashSet.fromIterable(allTerminals), allTerminals)

    val orderedClauses = topLevelClausesOrdered
            .fold(accumulatorWithTerminalsVisited, ::findClausesReachableFromClause)
            .orderedClauses
            .fold(Vector.empty(), Vector<Clause>::plus)

    return SortResult(orderedClauses, topLevelClausesOrdered, allTerminals)
}

private fun findTopLevelClauses(allRules: Seq<Rule>, lowestPrecedenceClauses: Seq<Clause>): Seq<Clause> {

    val allClausesUnordered =
        allRules
            .map { rule -> rule.labeledClause.clause }
            .fold(OrderedClauseAccumulator(), ::findClausesReachableFromClause)
            .orderedClauses
            .fold(HashSet.empty(), HashSet<Clause>::append)

    val topLevelClauses = Seq.fromIterable(
        allClausesUnordered
            .map { clause -> clause.labeledSubClauses }
            .fold(allClausesUnordered,
                    { accum, subClauses: Array<LabeledClause> ->
                        // Remove all clauses from the set that are subclauses, leaving only top-level clauses
                        val toRemove = subClauses
                                    .map    { labeledSubClause -> labeledSubClause.clause}
                                    .filter { subClause -> allClausesUnordered.contains(subClause) }
                        accum.removeAll(toRemove)
            })
        )

    // Add to the end of the list of toplevel clauses all lowest-precedence clauses, since
    // top-down precedence climbing should start at each lowest-precedence clause
    val topLevelClausesOrdered: Seq<Clause> =
            Seq.fromIterable(topLevelClauses).appendAll(lowestPrecedenceClauses)

    // Finally, in case there are cycles in the grammar that are not part of a precedence
    // hierarchy, add to the end of the list of toplevel clauses the set of all "head clauses"
    // of cycles (the set of all clauses reached twice in some path through the grammar)
    val cyclesInTopLevelClauses =
            topLevelClauses.fold(CycleHeadClauseAccumulator(), ::findCycleHeadClauses)

    val cyclesOutsidePrecedenceHierarchy =
            allRules.map { rule -> rule.labeledClause.clause }
                    .fold(cyclesInTopLevelClauses, ::findCycleHeadClauses)
                    .cycleHeadClauses

    topLevelClausesOrdered.appendAll(cyclesOutsidePrecedenceHierarchy)
    return topLevelClausesOrdered
}

/** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position.  */
private fun findClausesReachableFromClause(accumulator: OrderedClauseAccumulator, clause: Clause): OrderedClauseAccumulator {

    return if (accumulator.didVisit(clause)) {
        accumulator
    } else {
        clause.labeledSubClauses
                .map { subClause -> subClause.clause }
                .fold(accumulator.withVisitTo(clause), ::findClausesReachableFromClause)
                .withClause(clause)
    }
}

/** Find the [Clause] nodes that complete a cycle in the grammar.  */
private fun findCycleHeadClauses (accumulator: CycleHeadClauseAccumulator, clause: Clause): CycleHeadClauseAccumulator {
    require(clause !is RuleRef)
        { "There should not be any ${RuleRef::class.simpleName} nodes left in the grammar" }

    val incomingDiscovered = accumulator.discovered.add(clause)
    val incomingAccumulation = accumulator.withDiscovered(incomingDiscovered)

    val accumulated =
        clause.labeledSubClauses
            .map { labeledClause -> labeledClause.clause }
            .fold( incomingAccumulation,
                    { accum, subClause ->
                        (if (incomingDiscovered.contains(subClause)) {
                            // If the clauses discovered above and to here contain a subclause, we've reached a cycle
                            val newCycleHeadClauses = accum.cycleHeadClauses.add(subClause)
                            accum.withCycleHeadClauses(newCycleHeadClauses)
                        } else if (!incomingAccumulation.finished.contains(subClause)) {
                            // If we haven't already traversed a subclause, recurse into it.
                            findCycleHeadClauses(accum, subClause)
                        } else {
                            accum
                        })
                    })

    return CycleHeadClauseAccumulator(
                accumulated.discovered.removeValue(clause),     // Finished with this subtree
                accumulated.finished.add(clause),               // Mark traversal of this node
                accumulated.cycleHeadClauses
            )
}

    /** Find reachable clauses, and bottom-up (postorder), find clauses that always match in every position.  */
private fun findTerminals(accumulator: OrderedClauseAccumulator, clause: Clause): OrderedClauseAccumulator {
    return if (accumulator.didVisit(clause)) {
        accumulator
    } else if (clause is Terminal) {
        accumulator.withVisitTo(clause).withClause(clause)
    } else {
        clause.labeledSubClauses
                .map { subClause -> subClause.clause }
                .fold(accumulator.withVisitTo(clause), ::findTerminals)
    }
}

data class OrderedClauseAccumulator(val visited: HashSet<Clause> = HashSet.empty(),
                                    val orderedClauses: Seq<Clause> = Seq.empty()) {
    fun didVisit(clause: Clause) : Boolean {
        return visited.containsValue(clause)
    }
    fun withClause(clause: Clause): OrderedClauseAccumulator {
        return OrderedClauseAccumulator(visited, orderedClauses.append(clause))
    }
    fun withVisitTo(visit: Clause): OrderedClauseAccumulator {
        return OrderedClauseAccumulator(visited.append(visit), orderedClauses)
    }
}

data class CycleHeadClauseAccumulator(val discovered: HashSet<Clause> = HashSet.empty(),
                                      val finished: HashSet<Clause> = HashSet.empty(),
                                      val cycleHeadClauses: HashSet<Clause> = HashSet.empty()) {
    fun withDiscovered(newDiscovered: HashSet<Clause>) : CycleHeadClauseAccumulator {
        return CycleHeadClauseAccumulator(newDiscovered, finished, cycleHeadClauses)
    }
    fun withCycleHeadClauses(newCycleHeadClauses: HashSet<Clause>) : CycleHeadClauseAccumulator {
        return CycleHeadClauseAccumulator(discovered, finished, newCycleHeadClauses)
    }
}

