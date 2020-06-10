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
import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.grammar.Rule
import java.util.*
import kotlin.collections.ArrayList


/** Rewrite self-references in a precedence hierarchy into precedence-climbing form.  */
fun attachPrecedenceToSelfReferentialRuleNames(ruleNameWithoutPrecedence: String,
                                               rules: List<Rule>,
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
        val maybePreviousValue = precedenceToRule.put(rule.precedence, rule)
        require(maybePreviousValue == null)
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

    // Transform grammar rule to handle precedence
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
            rewriteSelfReferentialRuleRefs(rule.labeledClause.clause, rule.associativity, 0, numSelfRefs,
                    ruleNameWithoutPrecedence, currPrecRuleName, nextHighestPrecRuleName)
        }

        // Defer to next highest level of precedence if the rule doesn't match, except at the highest level of
        // precedence, which is assumed to be a precedence-breaking pattern (like parentheses), so should not
        // defer back to the lowest precedence level unless the pattern itself matches
        if (precedenceIdx < numPrecedenceLevels - 1) {
            // Move rule's toplevel clause (and any AST node label it has) into the first subclause of
            // a First clause that fails over to the next highest precedence level
            val first = First(rule.labeledClause.clause, RuleRef(nextHighestPrecRuleName))
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

/** Count number of self-references among descendent clauses.  */
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
private fun rewriteSelfReferentialRuleRefs(clause: Clause,
                                           associativity: Rule.Associativity?,
                                           numSelfRefsSoFar: Int,
                                           numSelfRefs: Int,
                                           selfRefRuleName: String,
                                           currPrecRuleName: String,
                                           nextHighestPrecRuleName: String): Int {
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
                                    if ((associativity === Rule.Associativity.LEFT && selfRefsSoFar == 0) ||
                                        (associativity === Rule.Associativity.RIGHT && selfRefsSoFar == numSelfRefs - 1)
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
                        clause.labeledSubClauses[i].clause = First(subClause, RuleRef(nextHighestPrecRuleName))
                    }
                    selfRefsSoFar++
                }
                // Else don't rewrite the RuleRef, it is not a self-ref
            } else {
                selfRefsSoFar = rewriteSelfReferentialRuleRefs(
                        subClause,
                        associativity,
                        selfRefsSoFar,
                        numSelfRefs,
                        selfRefRuleName,
                        currPrecRuleName,
                        nextHighestPrecRuleName
                )
            }
        }
    }
    return selfRefsSoFar
}
