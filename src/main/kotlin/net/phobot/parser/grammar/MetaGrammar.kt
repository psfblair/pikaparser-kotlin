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

import net.phobot.parser.ast.ASTNode
import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.ClauseFactory
import net.phobot.parser.clause.ClauseFactory.ast
import net.phobot.parser.clause.ClauseFactory.c
import net.phobot.parser.clause.ClauseFactory.cRange
import net.phobot.parser.clause.ClauseFactory.first
import net.phobot.parser.clause.ClauseFactory.nothing
import net.phobot.parser.clause.ClauseFactory.oneOrMore
import net.phobot.parser.clause.ClauseFactory.optional
import net.phobot.parser.clause.ClauseFactory.rule
import net.phobot.parser.clause.ClauseFactory.ruleRef
import net.phobot.parser.clause.ClauseFactory.seq
import net.phobot.parser.clause.ClauseFactory.start
import net.phobot.parser.clause.ClauseFactory.str
import net.phobot.parser.clause.ClauseFactory.zeroOrMore
import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.grammar.Rule.Associativity
import net.phobot.parser.utils.ParserInfo
import net.phobot.parser.utils.StringUtils



/**
 * A "meta-grammar" that produces a runtime parser generator, allowing a grammar to be defined using ASCII notation.
 */
object MetaGrammar {

    // Rule names:

    private const val GRAMMAR = "GRAMMAR"
    private const val WSC = "WSC"
    private const val COMMENT = "COMMENT"
    private const val RULE = "RULE"
    private const val CLAUSE = "CLAUSE"
    private const val IDENT = "IDENT"
    private const val PREC = "PREC"
    private const val NUM = "NUM"
    private const val NAME_CHAR = "NAME_CHAR"
    private const val CHAR_SET = "CHARSET"
    private const val HEX = "Hex"
    private const val CHAR_RANGE = "CHAR_RANGE"
    private const val CHAR_RANGE_CHAR = "CHAR_RANGE_CHAR"
    private const val QUOTED_STRING = "QUOTED_STR"
    private const val ESCAPED_CTRL_CHAR = "ESCAPED_CTRL_CHAR"
    private const val SINGLE_QUOTED_CHAR = "SINGLE_QUOTED_CHAR"
    private const val STR_QUOTED_CHAR = "STR_QUOTED_CHAR"
    private const val NOTHING = "NOTHING"
    private const val START = "START"

    // AST node names:

    private const val RULE_AST = "RuleAST"
    private const val PREC_AST = "PrecAST"
    private const val R_ASSOC_AST = "RAssocAST"
    private const val L_ASSOC_AST = "LAssocAST"
    private const val IDENT_AST = "IdentAST"
    private const val LABEL_AST = "LabelAST"
    private const val LABEL_NAME_AST = "LabelNameAST"
    private const val LABEL_CLAUSE_AST = "LabelClauseAST"
    private const val SEQ_AST = "SeqAST"
    private const val FIRST_AST = "FirstAST"
    private const val FOLLOWED_BY_AST = "FollowedByAST"
    private const val NOT_FOLLOWED_BY_AST = "NotFollowedByAST"
    private const val ONE_OR_MORE_AST = "OneOrMoreAST"
    private const val ZERO_OR_MORE_AST = "ZeroOrMoreAST"
    private const val OPTIONAL_AST = "OptionalAST"
    private const val SINGLE_QUOTED_CHAR_AST = "SingleQuotedCharAST"
    private const val CHAR_RANGE_AST = "CharRangeAST"
    private const val QUOTED_STRING_AST = "QuotedStringAST"
    private const val START_AST = "StartAST"
    private const val NOTHING_AST = "NothingAST"


    // Metagrammar:

    var grammar = Grammar(
            listOf(
                rule(GRAMMAR,
                        seq(start(), ruleRef(WSC), oneOrMore(ruleRef(RULE)))
                ),

                rule(RULE,
                        ast(RULE_AST,
                                seq(ruleRef(IDENT),
                                        ruleRef(WSC),
                                        optional(ruleRef(PREC)),
                                        str("<-"),
                                        ruleRef(WSC),
                                        ruleRef(CLAUSE),
                                        ruleRef(WSC),
                                        c(';'),
                                        ruleRef(WSC)))
                ),

                // Define precedence order for clause sequences

                // Parens
                rule(CLAUSE, precedence = 8, associativity = null,
                        clause = seq(c('('), ruleRef(WSC), ruleRef(CLAUSE), ruleRef(WSC), c(')'))
                ),

                // Terminals
                rule(CLAUSE, precedence = 7, associativity = null,
                        clause = first(
                                    ruleRef(IDENT),
                                    ruleRef(QUOTED_STRING),
                                    ruleRef(CHAR_SET),
                                    ruleRef(NOTHING),
                                    ruleRef(START))
                ),

                // OneOrMore / ZeroOrMore
                rule(CLAUSE, precedence = 6, associativity = null,
                        clause = first(
                                    seq(ast(ONE_OR_MORE_AST, ruleRef(CLAUSE)), ruleRef(WSC), c('+')),
                                    seq(ast(ZERO_OR_MORE_AST, ruleRef(CLAUSE)), ruleRef(WSC), c('*')))
                ),

                // FollowedBy / NotFollowedBy
                rule(CLAUSE, precedence = 5, associativity = null,
                        clause = first(
                                    seq(c('&'), ast(FOLLOWED_BY_AST, ruleRef(CLAUSE))),
                                    seq(c('!'), ast(NOT_FOLLOWED_BY_AST, ruleRef(CLAUSE))))
                ),

                // Optional
                rule(CLAUSE, precedence = 4, associativity = null,
                        clause = seq(ast(OPTIONAL_AST, ruleRef(CLAUSE)), ruleRef(WSC), c('?'))
                ),

                // ASTNodeLabel
                rule(CLAUSE, precedence = 3, associativity = null,
                        clause = ast(LABEL_AST,
                                        seq(ast(LABEL_NAME_AST,
                                                ruleRef(IDENT)),
                                                ruleRef(WSC),
                                                c(':'),
                                                ruleRef(WSC),
                                                ast(LABEL_CLAUSE_AST, ruleRef(CLAUSE)),
                                                ruleRef(WSC)))
                ),

                // Seq
                rule(CLAUSE, precedence = 2, associativity = null,
                        clause = ast(SEQ_AST,
                                        seq(ruleRef(CLAUSE),
                                            ruleRef(WSC),
                                            oneOrMore(seq(ruleRef(CLAUSE), ruleRef(WSC)))))
                ),

                // First
                rule(CLAUSE, precedence = 1, associativity = null,
                        clause = ast(FIRST_AST,
                                        seq(ruleRef(CLAUSE),
                                            ruleRef(WSC),
                                            oneOrMore(seq(c('/'), ruleRef(WSC), ruleRef(CLAUSE), ruleRef(WSC)))))
                ),

                // Whitespace or comment
                rule(WSC,
                        zeroOrMore(first(c(' ', '\n', '\r', '\t'), ruleRef(COMMENT)))
                ),

                // Comment
                rule(COMMENT,
                        seq(c('#'), zeroOrMore(c('\n').invert()))
                ),

                // Identifier
                rule(IDENT,
                        ast(IDENT_AST,
                                seq(ruleRef(NAME_CHAR), zeroOrMore(first(ruleRef(NAME_CHAR), cRange('0', '9')))))
                ),

                // Number
                rule(NUM,
                        oneOrMore(cRange('0', '9'))
                ),

                // Name character
                rule(NAME_CHAR,
                        c(cRange('a', 'z'), cRange('A', 'Z'), c('_', '-'))
                ),

                // Precedence and optional associativity modifiers for rule name
                rule(PREC,
                        seq(c('['),
                            ruleRef(WSC),
                            ast(PREC_AST, ruleRef(NUM)),
                            ruleRef(WSC),
                            optional(seq(c(','),
                                         ruleRef(WSC),
                                         first(ast(R_ASSOC_AST, first(c('r'), c('R'))),
                                               ast(L_ASSOC_AST, first(c('l'), c('L')))),
                                         ruleRef(WSC))),
                            c(']'), ruleRef(WSC))
                ),

                // Character set
                rule(CHAR_SET,
                        first(
                            seq(c('\''),
                                ast(SINGLE_QUOTED_CHAR_AST, ruleRef(SINGLE_QUOTED_CHAR)),
                                c('\'')
                            ),
                            seq(c('['),
                                ast(CHAR_RANGE_AST, seq(optional(c('^')),
                                                        oneOrMore(first(
                                                                    ruleRef(CHAR_RANGE),
                                                                    ruleRef(CHAR_RANGE_CHAR))))
                                ),
                                c(']')))
                ),

                // Single quoted character
                rule(SINGLE_QUOTED_CHAR,
                        first(
                            ruleRef(ESCAPED_CTRL_CHAR),
                            c('\'', '"').invert()) // TODO: replace invert() with NotFollowedBy
                ),

                // Char range
                rule(CHAR_RANGE,
                        seq(ruleRef(CHAR_RANGE_CHAR),
                            c('-'),
                            ruleRef(CHAR_RANGE_CHAR))
                ),

                // Char range character
                rule(CHAR_RANGE_CHAR,
                        first(
                            c('\\', ']').invert(),
                            ruleRef(ESCAPED_CTRL_CHAR),
                            str("\\\\"),
                            str("\\]"),
                            str("\\^"))
                ),

                // Quoted string
                rule(QUOTED_STRING,
                        seq(c('"'),
                            ast(QUOTED_STRING_AST,
                            zeroOrMore(ruleRef(STR_QUOTED_CHAR))),
                            c('"'))
                ),

                // Character within quoted string
                rule(STR_QUOTED_CHAR,
                        first(
                            ruleRef(ESCAPED_CTRL_CHAR),
                            c('"', '\\').invert()
                        )
                ),

                // Hex digit
                rule(HEX, c(cRange('0', '9'),
                            cRange('a', 'f'),
                            cRange('A', 'F'))
                ),

                // Escaped control character
                rule(ESCAPED_CTRL_CHAR,
                        first(
                                str("\\t"),
                                str("\\b"),
                                str("\\n"),
                                str("\\r"),
                                str("\\f"),
                                str("\\'"),
                                str("\\\""),
                                str("\\\\"),
                                seq(str("\\u"), ruleRef(HEX), ruleRef(HEX), ruleRef(HEX), ruleRef(HEX)))
                ),

                // Nothing (empty string match)
                rule(NOTHING,
                        ast(NOTHING_AST, seq(
                                            c('('),
                                            ruleRef(WSC),
                                            c(')')))
                ),

                // Match start position
                rule(START, ast(START_AST, c('^')))
            ))

    /** Recursively parse a list of AST nodes.  */
    private fun parseASTNodes(astNodes: List<ASTNode>): List<Clause> {
        val clauses = ArrayList<Clause>(astNodes.size)
        var nextNodeLabel: String? = null
        for (i in astNodes.indices) {
            val astNode = astNodes[i]
            // Create a Clause from the ASTNode
            var clause = parseASTNode(astNode)
            if (nextNodeLabel != null) {
                // Label the Clause with the preceding label, if present
                clause = ast(nextNodeLabel, clause)
                nextNodeLabel = null
            }
            clauses.add(clause)
        }
        return clauses
    }

    /** Recursively parse a single AST node.  */
    private fun parseASTNode(astNode: ASTNode): Clause {
        val clause: Clause
        when (astNode.label) {
            SEQ_AST                 -> clause = clauseWithMoreThanOneChild(astNode, MultiChildClauseType.SEQ)
            FIRST_AST               -> clause = clauseWithMoreThanOneChild(astNode, MultiChildClauseType.FIRST)
            ONE_OR_MORE_AST         -> clause = clauseWithOneChild(astNode,         SingleChildClauseType.ONE_OR_MORE)
            ZERO_OR_MORE_AST        -> clause = clauseWithOneChild(astNode,         SingleChildClauseType.ZERO_OR_MORE)
            OPTIONAL_AST            -> clause = clauseWithOneChild(astNode,         SingleChildClauseType.OPTIONAL)
            FOLLOWED_BY_AST         -> clause = clauseWithOneChild(astNode,         SingleChildClauseType.FOLLOWED_BY)
            NOT_FOLLOWED_BY_AST     -> clause = clauseWithOneChild(astNode,         SingleChildClauseType.NOT_FOLLOWED_BY)
            LABEL_AST               -> clause = ast(astNode.firstChild.text, parseASTNode(astNode.secondChild.firstChild))
            IDENT_AST               -> clause = ruleRef(astNode.text) // Rule name ref
            QUOTED_STRING_AST           // Doesn't include surrounding quotes
                                    -> clause = str(StringUtils.unescapeString(astNode.text))
            SINGLE_QUOTED_CHAR_AST  -> clause = c(StringUtils.unescapeChar(astNode.text))
            START_AST               -> clause = start()
            NOTHING_AST             -> clause = nothing()
            CHAR_RANGE_AST          -> clause = charRangeClause(astNode)
            else                        // Keep recursing for parens (the only type of AST node that doesn't have a label)
                                    -> clause = singleChild(astNode, typeName = "node")
        }
        return clause
    }

    private fun clauseWithOneChild(astNode: ASTNode, clauseType: SingleChildClauseType): Clause {
        val childASTNode = singleChild(astNode, clauseType.typeName)
        val clauseConstructor = clauseType.clauseConstructor
        return clauseConstructor(childASTNode)
    }

    private fun singleChild(astNode: ASTNode, typeName: String): Clause {
        val childAstNodes = parseASTNodes(astNode.children)
        val size = childAstNodes.size
        require(size == 1) { "Expected one subclause for ${typeName}, got $size: ${astNode}"}
        return childAstNodes[0]
    }

    enum class SingleChildClauseType(val typeName: String, val clauseConstructor: (Clause) -> Clause) {
        ONE_OR_MORE        ("OneOrMore", ClauseFactory::oneOrMore),
        ZERO_OR_MORE       ("ZeroOrMore", ClauseFactory::zeroOrMore),
        OPTIONAL           ("Optional", ClauseFactory::optional),
        FOLLOWED_BY        ("FollowedBy", ClauseFactory::followedBy),
        NOT_FOLLOWED_BY    ("NotFollowedBy", ClauseFactory::notFollowedBy)
    }

    private fun clauseWithMoreThanOneChild(astNode: ASTNode, clauseType: MultiChildClauseType): Clause {
        val childASTNodes = parseASTNodes(astNode.children).toTypedArray()
        val size = childASTNodes.size
        require (size > 1) { "Must have at least 2 child AST nodes for ${clauseType.typeName}, got ${size}: ${astNode}" }

        val nodesAfterSecond = childASTNodes.slice(2..size).toTypedArray()
        val clauseConstructor = clauseType.clauseConstructor
        return clauseConstructor(childASTNodes[0], childASTNodes[1], nodesAfterSecond)
    }

    enum class MultiChildClauseType(val typeName: String, val clauseConstructor: (Clause, Clause, Array<Clause>) -> Clause) {
        SEQ   ("Seq", ClauseFactory::seq),
        FIRST ("First", ClauseFactory::first),
    }

    private fun charRangeClause(astNode: ASTNode): Clause {
        var text = StringUtils.unescapeString(astNode.text)
        val invert = text.startsWith("^")
        if (invert) {
            text = text.substring(1)
        }
        return if (invert) cRange(text).invert() else cRange(text)
    }

    /** Parse a rule in the AST, returning a new [Rule].  */
    private fun parseRule(ruleNode: ASTNode): Rule {
        val ruleName = ruleNode.firstChild.text
        val hasPrecedence = ruleNode.children.size > 2
        val associativity = when {
            ruleNode.children.size < 4               -> null
            ruleNode.thirdChild.label == L_ASSOC_AST -> Associativity.LEFT
            ruleNode.thirdChild.label == R_ASSOC_AST -> Associativity.RIGHT
            else                                     -> null
        }
        val precedence = if (hasPrecedence) Integer.parseInt(ruleNode.secondChild.text) else -1

        require(!hasPrecedence || precedence >= 0)
            { ("If there is precedence, it must be zero or positive (rule ${ruleName} " +
                    "has precedence level ${precedence})")
            }

        val astNode = ruleNode.getChild(ruleNode.children.size - 1)
        val clause = parseASTNode(astNode)
        return rule(ruleName, precedence, associativity, clause)
    }

    /** Parse a grammar description in an input string, returning a new [Grammar] object.  */
    fun parse(input: String): Grammar {
        val memoTable = grammar.parse(input)

        //        ParserInfo.printParseResult("GRAMMAR", grammar, memoTable, input,
        //                arrayOf("GRAMMAR", "RULE", "CLAUSE[0]"), showAllMatches = false)
        //
        //        println("\nParsed meta-grammar:")
        //        for (clause in MetaGrammar.grammar.allClauses) {
        //            println("    " + clause.toStringWithRuleNames())
        //        }

        val precedenceOfFirst = GrammarPrecedenceLevels.clauseTypeToPrecedence[First::class]
        val syntaxCoverageRuleNames = arrayOf(GRAMMAR, RULE, "${CLAUSE}[${precedenceOfFirst}]")
        val syntaxErrors = memoTable.getSyntaxErrors(*syntaxCoverageRuleNames)

        if (syntaxErrors.isEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors)
        }

        val topLevelRule = grammar.getRule(GRAMMAR)
        var topLevelRuleASTNodeLabel = topLevelRule.labeledClause.astNodeLabel
        if (topLevelRuleASTNodeLabel == null) {
            topLevelRuleASTNodeLabel = "<root>"
        }
        val topLevelMatches = grammar.getNonOverlappingMatches(GRAMMAR, memoTable)

        require(topLevelMatches.isNotEmpty())
            { "Toplevel rule \"$GRAMMAR\" did not match" }

        if (topLevelMatches.size > 1) {
            println("\nMultiple toplevel matches:")
            for (topLevelMatch in topLevelMatches) {
                val topLevelASTNode = ASTNode(topLevelRuleASTNodeLabel, topLevelMatch, input)
                println(topLevelASTNode)
            }
            throw IllegalArgumentException("Stopping")
        }

        val topLevelASTNode = ASTNode(topLevelRuleASTNodeLabel, topLevelMatches[0], input)

        // println(topLevelASTNode);

        val rules = ArrayList<Rule>()
        for (astNode in topLevelASTNode.children) {

            require(astNode.label == RULE_AST) { "Wrong node type" }

            val rule = parseRule(astNode)
            rules.add(rule)
        }
        return Grammar(rules)
    }
}
