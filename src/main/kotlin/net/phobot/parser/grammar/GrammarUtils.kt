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
import net.phobot.parser.clause.aux.LabeledClause
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.clause.terminal.Terminal
import net.phobot.parser.grammar.Rule.Associativity
import java.util.*

/** Grammar utils.  */
object GrammarUtils {
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

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Check there are no cycles in the clauses of rules, before [RuleRef] instances are resolved to direct
     * references.
     */
    fun checkNoRefCycles(clause: Clause, selfRefRuleName: String, visited: MutableSet<Clause>) {
        val wasAdded = visited.add(clause)

        require(wasAdded)
            { "Rules should not contain cycles when they are created: ${selfRefRuleName}" }

        for (labeledSubClause in clause.labeledSubClauses) {
            val subClause = labeledSubClause.clause
            checkNoRefCycles(subClause, selfRefRuleName, visited)
        }
        visited.remove(clause)
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Count number of self-references among descendant clauses.  */
    private fun countRuleSelfReferences(clause: Clause, ruleNameWithoutPrecedence: String): Int {
        return if (clause is RuleRef && clause.referencedRuleName == ruleNameWithoutPrecedence) {
            1
        } else {
            var numSelfRefs = 0
            for (labeledSubClause in clause.labeledSubClauses) {
                val subClause = labeledSubClause.clause
                numSelfRefs += countRuleSelfReferences(subClause, ruleNameWithoutPrecedence)
            }
            numSelfRefs
        }
    }

    /** Rewrite self-references into precedence-climbing form.  */
    private fun rewriteSelfReferences(clause: Clause, associativity: Associativity?, numSelfRefsSoFar: Int,
                                      numSelfRefs: Int, selfRefRuleName: String, currPrecRuleName: String, nextHighestPrecRuleName: String): Int {
        var selfRefsSoFar = numSelfRefsSoFar
        // Terminate recursion when all self-refs have been replaced
        if (selfRefsSoFar < numSelfRefs) {
            for (i in clause.labeledSubClauses.indices) {
                val labeledSubClause = clause.labeledSubClauses[i]
                val subClause = labeledSubClause.clause
                if (subClause is RuleRef) {
                    if (subClause.referencedRuleName == selfRefRuleName) {
                        if (numSelfRefs >= 2) {
                            // Change name of self-references to implement precedence climbing:
                            // For leftmost operand of left-recursive rule:
                            // E[i] <- E X E  =>  E[i] = E[i] X E[i+1]
                            // For rightmost operand of right-recursive rule:
                            // E[i] <- E X E  =>  E[i] = E[i+1] X E[i]
                            // For non-associative rule:
                            // E[i] = E E  =>  E[i] = E[i+1] E[i+1]
                            clause.labeledSubClauses[i].clause =
                                    RuleRef(
                                        if ((associativity === Associativity.LEFT && selfRefsSoFar == 0) ||
                                            (associativity === Associativity.RIGHT && selfRefsSoFar == numSelfRefs - 1)
                                        ) {
                                            currPrecRuleName
                                        } else {
                                            nextHighestPrecRuleName
                                        }
                                    )
                        } else /* numSelfRefs == 1 */ {
                            // Move subclause (and its AST node label, if any) inside a First clause that
                            // climbs precedence to the next level:
                            // E[i] <- X E Y  =>  E[i] <- X (E[i] / E[(i+1)%N]) Y
                            subClause.referencedRuleName = currPrecRuleName
                            clause.labeledSubClauses[i].clause = First(arrayOf(subClause, RuleRef(nextHighestPrecRuleName)))
                        }
                        selfRefsSoFar++
                    }
                    // Else don't rewrite the RuleRef, it is not a self-ref
                } else {
                    selfRefsSoFar = rewriteSelfReferences(subClause, associativity, selfRefsSoFar,
                            numSelfRefs, selfRefRuleName, currPrecRuleName, nextHighestPrecRuleName)
                }
            }
        }
        return selfRefsSoFar
    }

    /** Rewrite self-references in a precedence hierarchy into precedence-climbing form.  */
    fun handlePrecedence(ruleNameWithoutPrecedence: String, rules: List<Rule>,
                         lowestPrecedenceClauses: MutableList<Clause>,
                         ruleNameToLowestPrecedenceLevelRuleName: MutableMap<String, String>) {
        // Rewrite rules
        //
        // For all but the highest precedence level:
        //
        // E[0] <- E (Op E)+  =>  E[0] <- (E[1] (Op E[1])+) / E[1]
        // E[0,L] <- E Op E   =>  E[0] <- (E[0] Op E[1]) / E[1]
        // E[0,R] <- E Op E   =>  E[0] <- (E[1] Op E[0]) / E[1]
        // E[3] <- '-' E      =>  E[3] <- '-' (E[3] / E[4]) / E[4]
        //
        // For highest precedence level, next highest precedence wraps back to lowest precedence level:
        //
        // E[5] <- '(' E ')'  =>  E[5] <- '(' (E[5] / E[0]) ')'

        // Check there are no duplicate precedence levels
        val precedenceToRule = TreeMap<Int, Rule>()
        for (rule in rules) {
            require(precedenceToRule.put(rule.precedence, rule) == null)
                { ("Multiple rules with name ${ruleNameWithoutPrecedence}"
                    + if (rule.precedence == -1) "" else " and precedence ${rule.precedence}")
                }
        }
        // Get rules in ascending order of precedence
        val precedenceOrder = ArrayList(precedenceToRule.values)

        // Rename rules to include precedence level
        val numPrecedenceLevels = rules.size
        for (precedenceIdx in 0 until numPrecedenceLevels) {
            // Since there is more than one precedence level, update rule name to include precedence
            val rule = precedenceOrder[precedenceIdx]
            rule.ruleName += "[${rule.precedence}]"
        }

        // Transform grammar rule to handle precence
        for (precedenceIdx in 0 until numPrecedenceLevels) {
            val rule = precedenceOrder[precedenceIdx]

            // Count the number of self-reference operands
            val numSelfRefs = countRuleSelfReferences(rule.labeledClause.clause, ruleNameWithoutPrecedence)

            val currPrecRuleName = rule.ruleName
            val nextHighestPrecRuleName = precedenceOrder[(precedenceIdx + 1) % numPrecedenceLevels].ruleName

            // If a rule has 2+ self-references, and rule is associative, need rewrite rule for associativity
            if (numSelfRefs >= 1) {
                // Rewrite self-references to higher precedence or left- and right-recursive forms.
                // (the toplevel clause of the rule, rule.labeledClause.clause, can't be a self-reference,
                // since we already checked for that, and IllegalArgumentException would have been thrown.)
                rewriteSelfReferences(rule.labeledClause.clause, rule.associativity, 0, numSelfRefs,
                        ruleNameWithoutPrecedence, currPrecRuleName, nextHighestPrecRuleName)
            }

            // Defer to next highest level of precedence if the rule doesn't match, except at the highest level of
            // precedence, which is assumed to be a precedence-breaking pattern (like parentheses), so should not
            // defer back to the lowest precedence level unless the pattern itself matches
            if (precedenceIdx < numPrecedenceLevels - 1) {
                // Move rule's toplevel clause (and any AST node label it has) into the first subclause of
                // a First clause that fails over to the next highest precedence level
                val first = First(arrayOf(rule.labeledClause.clause, RuleRef(nextHighestPrecRuleName)))
                // Move any AST node label down into first subclause of new First clause, so that label doesn't
                // apply to the final failover rule reference
                first.labeledSubClauses[0].astNodeLabel = rule.labeledClause.astNodeLabel
                rule.labeledClause.astNodeLabel = null
                // Replace rule clause with new First clause
                rule.labeledClause.clause = first
            }
        }

        // Map the bare rule name (without precedence suffix) to the lowest precedence level rule name
        val lowestPrecRule = precedenceOrder[0]
        lowestPrecedenceClauses.add(lowestPrecRule.labeledClause.clause)
        ruleNameToLowestPrecedenceLevelRuleName[ruleNameWithoutPrecedence] = lowestPrecRule.ruleName
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Recursively call toString() on the clause tree for this [Rule], so that toString() values are cached
     * before [RuleRef] objects are replaced with direct references, and so that shared subclauses are only
     * matched once.
     */
    fun intern(clause: Clause, toStringToClause: MutableMap<String, Clause>): Clause {
        // Call toString() on (and intern) subclauses, bottom-up
        for (i in clause.labeledSubClauses.indices) {
            clause.labeledSubClauses[i].clause = intern(clause.labeledSubClauses[i].clause, toStringToClause)
        }
        // Call toString after recursing to child nodes
        val toStr = clause.toString()

        // Intern the clause based on the toString value if it isn't present (Kotlin getOrPut returns the value if added)
        // and return whatever clause is interned
        return toStringToClause.getOrPut(toStr, { clause } )
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Resolve [RuleRef] clauses to a reference to the named rule.  */
    fun resolveRuleRefs(labeledClause: LabeledClause, ruleNameToRule: Map<String, Rule>,
                        ruleNameToLowestPrecedenceLevelRuleName: Map<String, String>, visited: MutableSet<Clause>) {
        if (labeledClause.clause is RuleRef) {
            // Follow a chain of from name in RuleRef objects until a non-RuleRef is reached
            var currLabeledClause = labeledClause
            val visitedClauses = HashSet<Clause>()
            while (currLabeledClause.clause is RuleRef) {

                require(visitedClauses.add(currLabeledClause.clause))
                    { "Reached toplevel RuleRef cycle: ${currLabeledClause.clause}" }

                // Follow a chain of from name in RuleRef objects until a non-RuleRef is reached
                val refdRuleName = (currLabeledClause.clause as RuleRef).referencedRuleName

                // Check if the rule is the reference to the lowest precedence rule of a precedence hierarchy
                val lowestPrecRuleName = ruleNameToLowestPrecedenceLevelRuleName[refdRuleName]

                // Look up Rule based on rule name
                val refdRule = ruleNameToRule[lowestPrecRuleName ?: refdRuleName]
                        ?: throw IllegalArgumentException("Unknown rule name: $refdRuleName")
                currLabeledClause = refdRule.labeledClause
            }

            // Set current clause to a direct reference to the referenced rule
            labeledClause.clause = currLabeledClause.clause

            // Copy across AST node label, if any
            if (labeledClause.astNodeLabel == null) {
                labeledClause.astNodeLabel = currLabeledClause.astNodeLabel
            }
            // Stop recursing at RuleRef
        } else {
            if (visited.add(labeledClause.clause)) {
                val labeledSubClauses = labeledClause.clause.labeledSubClauses
                for (element in labeledSubClauses) {
                    // Recurse through subclause tree if subclause was not a RuleRef
                    resolveRuleRefs(element, ruleNameToRule, ruleNameToLowestPrecedenceLevelRuleName, visited)
                }
            }
        }
    }
}
