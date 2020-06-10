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
import net.phobot.parser.clause.aux.LabeledClause
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.grammar.Rule

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