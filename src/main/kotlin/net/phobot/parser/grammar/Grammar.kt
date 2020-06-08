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
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.terminal.Nothing
import net.phobot.parser.clause.terminal.Terminal
import net.phobot.parser.memotable.Match
import net.phobot.parser.memotable.MemoKey
import net.phobot.parser.memotable.MemoTable
import java.util.*
import java.util.stream.Collectors

/** A grammar. The [.parse] method runs the parser on the provided input string.  */
class Grammar
/** Construct a grammar from a set of rules.  */
  (rules: List<Rule>) {

    /** All rules in the grammar.  */
    private val allRules: List<Rule>

    /** All clausesin the grammar.  */
    val allClauses: List<Clause>

    /** A mapping from rule name (with any precedence suffix) to the corresponding [Rule].  */
    val ruleNameWithPrecedenceToRule: MutableMap<String, Rule>

    init {
        require(rules.isNotEmpty()) { "Grammar must consist of at least one rule" }

        // Group rules by name
        val ruleNameToRules = mutableMapOf<String, List<Rule>>()

        for (rule in rules) {
            val ruleName = rule.ruleName // Kotlin ensures this will never be null
            val referencedRuleName = (rule.labeledClause.clause as RuleRef).referencedRuleName

            require(rule.labeledClause.clause !is RuleRef || referencedRuleName != ruleName) {
                // Make sure rule doesn't refer only to itself
                "Rule cannot refer to only itself: $ruleName[${rule.precedence}]"
            }

            ruleNameToRules.getOrPut(ruleName, { mutableListOf() })

            // Make sure there are no cycles in the grammar before RuleRef instances have been replaced
            // with direct references (checking once up front simplifies other recursive routines, so that
            // they don't have to check for infinite recursion)
            GrammarUtils.checkNoRefCycles(rule.labeledClause.clause, ruleName, mutableSetOf())
        }

        allRules = ArrayList(rules)
        val ruleNameToLowestPrecedenceLevelRuleName = mutableMapOf<String, String>()
        val lowestPrecedenceClauses = mutableListOf<Clause>()
        for (ent in ruleNameToRules.entries) {
            // Rewrite rules that have multiple precedence levels, as described in the paper
            val rulesWithName = ent.value
            if (rulesWithName.size > 1) {
                val ruleName = ent.key
                GrammarUtils.handlePrecedence(
                    ruleName, rulesWithName, lowestPrecedenceClauses, ruleNameToLowestPrecedenceLevelRuleName
                )
            }
        }

        // If there is more than one precedence level for a rule, the handlePrecedence call above modifies
        // rule names to include a precedence suffix, and also adds an all-precedence selector clause with the
        // original rule name. All rule names should now be unique.
        ruleNameWithPrecedenceToRule = mutableMapOf()
        for (rule in allRules) {
            // The handlePrecedence call above added the precedence to the rule name as a suffix
            val maybeExistingRule = ruleNameWithPrecedenceToRule.put(rule.ruleName, rule)
            // Should not happen
           require(maybeExistingRule == null) { "Duplicate rule name ${rule.ruleName}" }
        }

        // Register each rule with its toplevel clause (used in the clause's toString() method)
        for (rule in allRules) {
            rule.labeledClause.clause.registerRule(rule)
        }

        // Intern clauses based on their toString() value, coalescing shared sub-clauses into a DAG, so that
        // effort is not wasted parsing different instances of the same clause multiple times, and so that
        // when a subclause matches, all parent clauses will be added to the active set in the next iteration.
        // Also causes the toString() values to be cached, so that after RuleRefs are replaced with direct
        // Clause references, toString() doesn't get stuck in an infinite loop.
        val toStringToClause = HashMap<String, Clause>()
        for (rule in allRules) {
            rule.labeledClause.clause = GrammarUtils.intern(rule.labeledClause.clause, toStringToClause)
        }

        // Resolve each RuleRef into a direct reference to the referenced clause
        val clausesVisitedResolveRuleRefs = HashSet<Clause>()
        for (rule in allRules) {
            GrammarUtils.resolveRuleRefs(rule.labeledClause, ruleNameWithPrecedenceToRule,
                    ruleNameToLowestPrecedenceLevelRuleName, clausesVisitedResolveRuleRefs)
        }

        // Topologically sort clauses, bottom-up, placing the result in allClauses
        allClauses = GrammarUtils.findClauseTopoSortOrder(allRules, lowestPrecedenceClauses)

        // Find clauses that always match zero or more characters, e.g. FirstMatch(X | Nothing).
        // Importantly, allClauses is in reverse topological order, i.e. traversal is bottom-up.
        for (clause in allClauses) {
            clause.determineWhetherCanMatchZeroChars()
        }

        // Find seed parent clauses (in the case of Seq, this depends upon alwaysMatches being set in the prev step)
        for (clause in allClauses) {
            clause.addAsSeedParentClause()
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Main parsing method.  */
    fun parse(input: String): MemoTable {
        val priorityQueue = PriorityQueue<Clause> { c1, c2 -> c1.clauseIdx - c2.clauseIdx }

        val memoTable = MemoTable(grammar = this, input = input)

        val terminals = allClauses
                .stream()
                .filter { clause -> (clause is Terminal
                                    // Don't match Nothing everywhere -- it always matches
                                    && clause !is Nothing)
                        }
                .collect(Collectors.toList())

        // Main parsing loop
        for (startPos in input.length - 1 downTo 0) {
            priorityQueue.addAll(terminals)
            while (!priorityQueue.isEmpty()) {
                // Remove a clause from the priority queue (ordered from terminals to toplevel clauses)
                val clause = priorityQueue.remove()
                val memoKey = MemoKey(clause, startPos)
                val match = clause.match(memoTable, memoKey, input)
                memoTable.addMatch(memoKey, match, priorityQueue)
            }
        }
        return memoTable
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Get a rule by name.  */
    fun getRule(ruleNameWithPrecedence: String): Rule {
        val rule = ruleNameWithPrecedenceToRule[ruleNameWithPrecedence]
        requireNotNull(rule) { "Unknown rule name: $ruleNameWithPrecedence" }
        return rule!! //This compiles without !! but the editor has a bug that shows it as an error.
    }

    /**
     * Get the [Match] entries for all nonoverlapping matches of the named rule, obtained by greedily matching
     * from the beginning of the string, then looking for the next match after the end of the current match.
     */
    fun getNonOverlappingMatches(ruleName: String, memoTable: MemoTable): List<Match> {
        return memoTable.getNonOverlappingMatches(getRule(ruleName).labeledClause.clause)
    }

    /** Get all [Match] entries for the given clause, indexed by start position.  */
    fun getNavigableMatches(ruleName: String, memoTable: MemoTable): NavigableMap<Int, Match> {
        return memoTable.getNavigableMatches(getRule(ruleName).labeledClause.clause)
    }

    companion object {
        /** If true, print verbose debug output.  */
        var DEBUG = false
    }
}
