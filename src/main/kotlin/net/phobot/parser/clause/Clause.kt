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

package net.phobot.parser.clause

import net.phobot.parser.clause.aux.ASTNodeLabel
import net.phobot.parser.clause.aux.LabeledClause
import net.phobot.parser.clause.nonterminal.Seq
import net.phobot.parser.clause.terminal.Nothing
import net.phobot.parser.grammar.GrammarPrecedenceLevels
import net.phobot.parser.grammar.Rule
import net.phobot.parser.memotable.Match
import net.phobot.parser.memotable.MemoKey
import net.phobot.parser.memotable.MemoTable
import java.util.*
import java.util.stream.Collectors.joining
import kotlin.streams.asStream
import kotlin.streams.toList

/** Abstract superclass of all PEG operators and terminals.  */
abstract class Clause
// -------------------------------------------------------------------------------------------------------------

/** Clause constructor.  */
protected constructor(vararg subClauses: Clause) {
    /** Subclauses, paired with their AST node label (if there is one).  */
    val labeledSubClauses: Array<LabeledClause>

    init {
        require(subClauses.isEmpty() || subClauses[0] !is Nothing)
        { // Nothing can't be the first subclause, since we don't trigger upwards expansion of the DP wavefront
          // by seeding the memo table by matching Nothing at every input position, to keep the memo table small
          "${Nothing::class.simpleName} cannot be the first subclause of any clause"
        }

        this.labeledSubClauses =
                subClauses
                        .asSequence()
                        .asStream()
                        .map { subClause ->
                            if (subClause is ASTNodeLabel) {
                                LabeledClause(
                                        // We will skip over the ASTNodeLabel node when adding subClause to subClauses array
                                        subClause.labeledSubClauses[0].clause,
                                        // We will transfer ASTNodeLabel.astNodeLabel to LabeledClause.astNodeLabel field
                                        subClause.astNodeLabel
                                )
                            } else {
                                LabeledClause(subClause, null)
                            }
                        }
                        .toList()
                        .toTypedArray()
    }


    /** Rules this clause is a toplevel clause of (used by [}][.toStringWithRuleNames] */
    val rules: MutableList<Rule> = mutableListOf()

    /** The parent clauses of this clause that should be matched in the same start position.  */
    val seedParentClauses: MutableList<Clause> = mutableListOf()

    /** If true, the clause can match while consuming zero characters.  */
    var canMatchZeroChars: Boolean = false

    /** Index in the topological sort order of clauses, bottom-up.  */
    var clauseIdx: Int = 0

    /** The cached result of the [.toString] method.  */
    private var toStringCached: String? = null

    /** The cached result of the [.toStringWithRuleNames] method.  */
    private var toStringWithRuleNameCached: String? = null

    // -------------------------------------------------------------------------------------------------------------

    /** Get the names of rules that this clause is the root clause of.  */
    val ruleNames: String
        get() =
            rules
                    .stream()
                    .map { rule -> rule.ruleName }
                    .sorted()
                    .collect(joining(", "))

    /** Register this clause with a rule (used by [.toStringWithRuleNames]).  */
    fun registerRule(rule: Rule) {
        rules.add(rule)
    }

    /** Unregister this clause from a rule.  */
    fun unregisterRule(rule: Rule) {
        rules.remove(rule)
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Find which subclauses need to add this clause as a "seed parent clause". Overridden in [Seq].  */
    open fun addAsSeedParentClause() {
        // Default implementation: all subclauses will seed this parent clause.
        val added = HashSet<Any>()
        for (labeledSubClause in labeledSubClauses) {
            // Don't duplicate seed parent clauses in the subclause
            if (added.add(labeledSubClause.clause)) {
                labeledSubClause.clause.seedParentClauses.add(this)
            }
        }
    }

    /**
     * Sets [.canMatchZeroChars] to true if this clause can match zero characters, i.e. always matches at any
     * input position. Called bottom-up. Implemented in subclasses.
     */
    abstract fun determineWhetherCanMatchZeroChars()

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Match a clause by looking up its subclauses in the memotable (in the case of nonterminals), or by looking at
     * the input string (in the case of terminals). Implemented in subclasses.
     */
    abstract fun match(memoTable: MemoTable, memoKey: MemoKey, input: String): Match?

    override fun toString(): String {
        throw IllegalArgumentException("toString() needs to be overridden in subclasses")
    }

    /** Get the clause as a string, with rule names prepended if the clause is the toplevel clause of a rule.  */
    fun toStringWithRuleNames(): String {
        return updateStringWithRuleNameCacheIfNecessary {
            val buf = StringBuilder()
            // Add rule names
            buf.append(ruleNames)
            buf.append(" <- ")
            // Add any AST node labels
            var addedASTNodeLabels = false
            for (i in rules.indices) {
                val rule = rules[i]
                if (rule.labeledClause.astNodeLabel != null) {
                    buf.append(rule.labeledClause.astNodeLabel + ":")
                    addedASTNodeLabels = true
                }
            }
            val addParens = addedASTNodeLabels && GrammarPrecedenceLevels.needToAddParensAroundASTNodeLabel(this)
            if (addParens) {
                buf.append('(')
            }
            buf.append(toString())
            if (addParens) {
                buf.append(')')
            }
            buf.toString()
        }
    }

    fun updateStringCacheIfNecessary(stringGenerator: () -> String): String {
        if (toStringCached == null) {
            toStringCached = stringGenerator()
        }
        return toStringCached as String
    }

    private fun updateStringWithRuleNameCacheIfNecessary(stringGenerator: () -> String): String {
        if (toStringWithRuleNameCached == null) {
            toStringWithRuleNameCached = stringGenerator()
        }
        return toStringWithRuleNameCached as String
    }
}
