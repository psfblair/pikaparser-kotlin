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

import net.phobot.parser.ast.ASTNode
import net.phobot.parser.ast.RULE_AST
import net.phobot.parser.ast.PREC_AST
import net.phobot.parser.ast.R_ASSOC_AST
import net.phobot.parser.ast.L_ASSOC_AST
import net.phobot.parser.ast.IDENT_AST
import net.phobot.parser.ast.LABEL_AST
import net.phobot.parser.ast.LABEL_NAME_AST
import net.phobot.parser.ast.LABEL_CLAUSE_AST
import net.phobot.parser.ast.SEQ_AST
import net.phobot.parser.ast.FIRST_AST
import net.phobot.parser.ast.FOLLOWED_BY_AST
import net.phobot.parser.ast.NOT_FOLLOWED_BY_AST
import net.phobot.parser.ast.ONE_OR_MORE_AST
import net.phobot.parser.ast.ZERO_OR_MORE_AST
import net.phobot.parser.ast.OPTIONAL_AST
import net.phobot.parser.ast.SINGLE_QUOTED_CHAR_AST
import net.phobot.parser.ast.CHAR_RANGE_AST
import net.phobot.parser.ast.QUOTED_STRING_AST
import net.phobot.parser.ast.START_AST
import net.phobot.parser.ast.NOTHING_AST

import net.phobot.parser.clause.Clause
import net.phobot.parser.clause.ClauseFactory.oneOrMore
import net.phobot.parser.clause.ClauseFactory.optional
import net.phobot.parser.clause.ClauseFactory.zeroOrMore
import net.phobot.parser.clause.ClauseFactory.cRange
import net.phobot.parser.clause.ClauseFactory.str
import net.phobot.parser.clause.aux.ASTNodeLabel
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.clause.nonterminal.Seq
import net.phobot.parser.clause.terminal.CharSet
import net.phobot.parser.clause.terminal.Start
import net.phobot.parser.grammar.RuleParser.rule
import net.phobot.parser.utils.ParserInfo


/**
 * A "meta-grammar" that produces a runtime parser generator, allowing a grammar to be defined using ASCII notation.
 */
object MetaGrammar {

    // Rule names:

    private const val GRAMMAR_RULENAME = "GRAMMAR"
    private const val WSC_RULENAME = "WSC"
    private const val COMMENT_RULENAME = "COMMENT"
    private const val START_RULENAME = "START"
    private const val RULE_RULENAME = "RULE"
    private const val CLAUSE_RULENAME = "CLAUSE"
    private const val IDENTIFIER_RULENAME = "IDENT"
    private const val PRECEDENCE_RULENAME = "PREC"
    private const val NUMBER_RULENAME = "NUM"
    private const val HEX_DIGIT_RULENAME = "HEX"
    private const val NAME_CHAR_RULENAME = "NAME_CHAR"
    private const val CHAR_SET_RULENAME = "CHARSET"
    private const val CHAR_RANGE_RULENAME = "CHAR_RANGE"
    private const val CHAR_RANGE_CHAR_RULENAME = "CHAR_RANGE_CHAR"
    private const val QUOTED_STRING_RULENAME = "QUOTED_STR"
    private const val SINGLE_QUOTED_CHAR_RULENAME = "SINGLE_QUOTED_CHAR"
    private const val STR_QUOTED_CHAR_RULENAME = "STR_QUOTED_CHAR"
    private const val ESCAPED_CTRL_CHAR_RULENAME = "ESCAPED_CTRL_CHAR"
    private const val NOTHING_RULENAME = "NOTHING"

    // Metagrammar:

    // These are all functions because we can't hold one static instance to a single rule reference 
    // (because e.g., of how rules are renamed for precedence). So we use constructor functions.
    private val START                   = { Start() }
    private val START_REF               = { RuleRef(START_RULENAME)  }
    private val WHITESPACE_REF          = { RuleRef(WSC_RULENAME) }
    private val RULE_REFS               = { oneOrMore(RuleRef(RULE_RULENAME)) }
    private val IDENTIFIER_REF          = { RuleRef(IDENTIFIER_RULENAME) }
    private val PRECEDENCE_REF          = { RuleRef(PRECEDENCE_RULENAME) }
    private val CLAUSE_REF              = { RuleRef(CLAUSE_RULENAME) }
    private val CHAR_SET_REF            = { RuleRef(CHAR_SET_RULENAME) }
    private val SINGLE_QUOTED_CHAR_REF  = { RuleRef(SINGLE_QUOTED_CHAR_RULENAME) }
    private val QUOTED_STRING_REF       = { RuleRef(QUOTED_STRING_RULENAME) }
    private val NAME_CHAR_REF           = { RuleRef(NAME_CHAR_RULENAME) }
    private val HEX_DIGIT_REF           = { RuleRef(HEX_DIGIT_RULENAME) }
    private val NUMBER_REF              = { RuleRef(NUMBER_RULENAME) }
    private val COMMENT_REF             = { RuleRef(COMMENT_RULENAME) }
    private val CHAR_RANGE_REF          = { RuleRef(CHAR_RANGE_RULENAME) }
    private val CHAR_RANGE_CHAR_REF     = { RuleRef(CHAR_RANGE_CHAR_RULENAME) }
    private val ESCAPED_CTRL_CHAR_REF   = { RuleRef(ESCAPED_CTRL_CHAR_RULENAME) }
    private val STR_QUOTED_CHAR_REF     = { RuleRef(STR_QUOTED_CHAR_RULENAME) }
    private val NOTHING_REF             = { RuleRef(NOTHING_RULENAME) }

    private val SEMICOLON = CharSet(';')
    private val COMMA = CharSet(',')
    private val OPEN_PAREN = CharSet('(')
    private val CLOSE_PAREN = CharSet(')')
    private val OPEN_BRACKET = CharSet('[')
    private val CLOSE_BRACKET = CharSet(']')
    private val DOUBLE_QUOTE = CharSet('"')
    private val ARROW = str("<-")
    private val PLUS = CharSet('+')
    private val STAR = CharSet('*')
    private val AMPERSAND = CharSet('&')
    private val BANG = CharSet('!')
    private val QUESTION_MARK = CharSet('?')
    private val COLON = CharSet(':')
    private val SLASH = CharSet('/')
    private val BACKSLASH = CharSet('\'')
    private val CARET = CharSet('^')
    private val HASH = CharSet('#')
    private val HYPHEN = CharSet('-')
    private val NEWLINE = CharSet('\n')
    private val NON_NEWLINE = NEWLINE.invert()
    private val WHITESPACE_CHARS = CharSet(charArrayOf(' ', '\n', '\r', '\t'))
    private val DIGITS = cRange('0', '9')
    private val NAME_CHARS = CharSet(cRange('a', 'z'), cRange('A', 'Z'), CharSet('_', '-'))
    private val LOWER_OR_UPPERCASE_R: Array<Clause> = arrayOf(CharSet('r'), CharSet('R'))
    private val LOWER_OR_UPPERCASE_L: Array<Clause> = arrayOf(CharSet('l'), CharSet('L'))
    private val LOWERCASE_A_TO_F = cRange('a', 'f')
    private val UPPERCASE_A_TO_F = cRange('A', 'F')

    private val ESCAPED_TAB = str("\\t")
    private val ESCAPED_BELL = str("\\b")
    private val ESCAPED_NEWLINE = str("\\n")
    private val ESCAPED_RETURN = str("\\r")
    private val ESCAPED_FORM_FEED = str("\\f")
    private val ESCAPED_LOWERCASE_U = str("\\u")
    private val ESCAPED_SINGLE_QUOTE = str("\\'")
    private val ESCAPED_DOUBLE_QUOTE = str("\\\"")
    private val ESCAPED_BACKSLASH = str("\\\\")
    private val ESCAPED_CLOSE_BRACKET = str("\\]")
    private val ESCAPED_CARET = str("\\^")
    private val CHARS_EXCEPT_SINGLE_QUOTE = CharSet('\'').invert()
    private val CHARS_EXCEPT_DOUBLE_QUOTE_AND_BACKSLASH = CharSet('"', '\\').invert()
    private val CHARS_EXCEPT_BACKSLASH_OR_SQUARE_BRACKET = CharSet('\\', ']').invert()

    var grammar = Grammar(
            listOf(
                    rule(GRAMMAR_RULENAME, Seq(START(), WHITESPACE_REF(), RULE_REFS())),

                    rule(RULE_RULENAME, ASTNodeLabel(RULE_AST,
                            Seq(
                                IDENTIFIER_REF(),
                                WHITESPACE_REF(),
                                optional(PRECEDENCE_REF()),
                                ARROW,
                                WHITESPACE_REF(),
                                CLAUSE_REF(),
                                WHITESPACE_REF(),
                                SEMICOLON,
                                WHITESPACE_REF()
                            ))
                    ),

                    // Define precedence order for clause Sequences

                    // Parens
                    rule(CLAUSE_RULENAME, precedence = 8, associativity = null,
                            clause = Seq(
                                        OPEN_PAREN,
                                        WHITESPACE_REF(),
                                        CLAUSE_REF(),
                                        WHITESPACE_REF(),
                                        CLOSE_PAREN
                                        )
                    ),

                    // Terminals
                    rule(CLAUSE_RULENAME, precedence = 7, associativity = null,
                            clause = First(
                                        IDENTIFIER_REF(),
                                        QUOTED_STRING_REF(),
                                        CHAR_SET_REF(),
                                        NOTHING_REF(),
                                        START_REF()
                                        )
                    ),

                    // OneOrMore / ZeroOrMore
                    rule(CLAUSE_RULENAME, precedence = 6, associativity = null,
                            clause = First(
                                        Seq(
                                            ASTNodeLabel(ONE_OR_MORE_AST, CLAUSE_REF()),
                                            WHITESPACE_REF(),
                                            PLUS
                                        ),
                                        Seq(
                                            ASTNodeLabel(ZERO_OR_MORE_AST, CLAUSE_REF()),
                                            WHITESPACE_REF(),
                                            STAR
                                        ))
                    ),

                    // FollowedBy / NotFollowedBy
                    rule(CLAUSE_RULENAME, precedence = 5, associativity = null,
                            clause = First(
                                        Seq(
                                            AMPERSAND,
                                            ASTNodeLabel(FOLLOWED_BY_AST, CLAUSE_REF())
                                        ),
                                        Seq(
                                            BANG,
                                            ASTNodeLabel(NOT_FOLLOWED_BY_AST, CLAUSE_REF())
                                        ))
                    ),

                    // Optional
                    rule(CLAUSE_RULENAME, precedence = 4, associativity = null,
                            clause = Seq(
                                        ASTNodeLabel(OPTIONAL_AST, CLAUSE_REF()),
                                        WHITESPACE_REF(),
                                        QUESTION_MARK
                                        )
                    ),

                    // ASTNodeLabel
                    rule(CLAUSE_RULENAME, precedence = 3, associativity = null,
                            clause = ASTNodeLabel(LABEL_AST,
                                    Seq(
                                        ASTNodeLabel(LABEL_NAME_AST, IDENTIFIER_REF()),
                                        WHITESPACE_REF(),
                                        COLON,
                                        WHITESPACE_REF(),
                                        ASTNodeLabel(LABEL_CLAUSE_AST, CLAUSE_REF()),
                                        WHITESPACE_REF()
                                    )
                            )
                    ),

                    // Seq
                    rule(CLAUSE_RULENAME, precedence = 2, associativity = null,
                            clause = ASTNodeLabel(SEQ_AST,
                                        Seq(
                                            CLAUSE_REF(),
                                            WHITESPACE_REF(),
                                            oneOrMore(
                                                Seq(
                                                    CLAUSE_REF(),
                                                    WHITESPACE_REF()
                                                ))
                                        ))
                    ),

                    // First
                    rule(CLAUSE_RULENAME, precedence = 1, associativity = null,
                            clause = ASTNodeLabel(FIRST_AST,
                                        Seq(
                                            CLAUSE_REF(),
                                            WHITESPACE_REF(),
                                            oneOrMore(
                                                Seq(
                                                    SLASH,
                                                    WHITESPACE_REF(),
                                                    CLAUSE_REF(),
                                                    WHITESPACE_REF()
                                                )
                                            )
                                        ))
                    ),

                    // Whitespace or comment
                    rule(WSC_RULENAME,
                            zeroOrMore(
                                First(
                                    WHITESPACE_CHARS,
                                    COMMENT_REF()
                                )
                            )
                    ),

                    // Comment
                    rule(COMMENT_RULENAME,
                            Seq(
                                HASH,
                                zeroOrMore(NON_NEWLINE)
                            )
                    ),

                    // Identifier
                    rule(IDENTIFIER_RULENAME,
                            ASTNodeLabel(IDENT_AST,
                                Seq(
                                    NAME_CHAR_REF(),
                                    zeroOrMore(
                                        First(
                                            NAME_CHAR_REF(),
                                            DIGITS
                                        )
                                    )
                                ))
                    ),

                    // Number
                    rule(NUMBER_RULENAME, oneOrMore(DIGITS)),

                    // Name character
                    rule(NAME_CHAR_RULENAME, NAME_CHARS),

                    // Precedence and optional associativity modifiers for rule name
                    rule(PRECEDENCE_RULENAME,
                            Seq(
                                OPEN_BRACKET,
                                WHITESPACE_REF(),
                                ASTNodeLabel(PREC_AST, NUMBER_REF()),
                                WHITESPACE_REF(),
                                optional(
                                    Seq(
                                        COMMA,
                                        WHITESPACE_REF(),
                                        First(
                                            ASTNodeLabel(R_ASSOC_AST, First(*LOWER_OR_UPPERCASE_R)),
                                            ASTNodeLabel(L_ASSOC_AST, First(*LOWER_OR_UPPERCASE_L))
                                        ),
                                        WHITESPACE_REF()
                                    )),
                                CLOSE_BRACKET,
                                WHITESPACE_REF()
                            )
                    ),

                    // Character set
                    rule(CHAR_SET_RULENAME,
                            First(
                                Seq(
                                    BACKSLASH,
                                    ASTNodeLabel(SINGLE_QUOTED_CHAR_AST, SINGLE_QUOTED_CHAR_REF()),
                                    BACKSLASH
                                ),
                                Seq(
                                    OPEN_BRACKET,
                                    ASTNodeLabel(CHAR_RANGE_AST,
                                        Seq(
                                            optional(CARET),
                                            oneOrMore(
                                                First(
                                                    CHAR_RANGE_REF(),
                                                    CHAR_RANGE_CHAR_REF()
                                                )
                                            )
                                        )
                                    ),
                                    CLOSE_BRACKET
                                )
                            )
                    ),

                    // Single quoted character
                    rule(SINGLE_QUOTED_CHAR_RULENAME,
                            First(
                                ESCAPED_CTRL_CHAR_REF(),
                                CHARS_EXCEPT_SINGLE_QUOTE // TODO: replace invert() with NotFollowedBy
                            )
                    ),

                    // Char range
                    rule(CHAR_RANGE_RULENAME,
                            Seq(
                                CHAR_RANGE_CHAR_REF(),
                                HYPHEN,
                                CHAR_RANGE_CHAR_REF()
                            )
                    ),

                    // Char range character
                    rule(CHAR_RANGE_CHAR_RULENAME,
                            First(
                                CHARS_EXCEPT_BACKSLASH_OR_SQUARE_BRACKET,
                                ESCAPED_CTRL_CHAR_REF(),
                                ESCAPED_BACKSLASH,
                                ESCAPED_CLOSE_BRACKET,
                                ESCAPED_CARET
                            )
                    ),

                    // Quoted string
                    rule(QUOTED_STRING_RULENAME,
                            Seq(
                                DOUBLE_QUOTE,
                                ASTNodeLabel(QUOTED_STRING_AST, zeroOrMore(STR_QUOTED_CHAR_REF())),
                                DOUBLE_QUOTE
                            )
                    ),

                    // Character within quoted string
                    rule(STR_QUOTED_CHAR_RULENAME,
                            First(
                                ESCAPED_CTRL_CHAR_REF(),
                                CHARS_EXCEPT_DOUBLE_QUOTE_AND_BACKSLASH
                            )
                    ),

                    // Hex digit
                    rule(HEX_DIGIT_RULENAME,
                            CharSet(
                                DIGITS,
                                LOWERCASE_A_TO_F,
                                UPPERCASE_A_TO_F
                            )
                    ),

                    // Escaped control character
                    rule(ESCAPED_CTRL_CHAR_RULENAME,
                            First(
                                ESCAPED_TAB,
                                ESCAPED_BELL,
                                ESCAPED_NEWLINE,
                                ESCAPED_RETURN,
                                ESCAPED_FORM_FEED,
                                ESCAPED_SINGLE_QUOTE,
                                ESCAPED_DOUBLE_QUOTE,
                                ESCAPED_BACKSLASH,
                                Seq(
                                    ESCAPED_LOWERCASE_U, HEX_DIGIT_REF(), HEX_DIGIT_REF(), HEX_DIGIT_REF(), HEX_DIGIT_REF()
                                )
                            )
                    ),

                    // Nothing (empty string match)
                    rule(NOTHING_RULENAME,
                            ASTNodeLabel(NOTHING_AST,
                                Seq(
                                    OPEN_PAREN,
                                    WHITESPACE_REF(),
                                    CLOSE_PAREN
                                )
                            )
                    ),

                    // Match start position
                    rule(START_RULENAME, ASTNodeLabel(START_AST, CARET))
            ))

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

        val precedenceOfFirst = PrecedenceLevels.clauseTypeToPrecedence[First::class]
        val syntaxErrors = memoTable.getSyntaxErrors(arrayOf(
                GRAMMAR_RULENAME, RULE_RULENAME, "${CLAUSE_RULENAME}[${precedenceOfFirst}]")
        )

        if (syntaxErrors.isNotEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors)
        }

        val topLevelRule = grammar.getRule(GRAMMAR_RULENAME)
        var topLevelRuleASTNodeLabel = topLevelRule.labeledClause.astNodeLabel
        if (topLevelRuleASTNodeLabel == null) {
            topLevelRuleASTNodeLabel = "<root>"
        }
        val topLevelMatches = grammar.getNonOverlappingMatches(GRAMMAR_RULENAME, memoTable)

        require(topLevelMatches.isNotEmpty())
        { "Toplevel rule \"$GRAMMAR_RULENAME\" did not match" }

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

            val rule = RuleParser.parseRule(astNode)
            rules.add(rule)
        }
        return Grammar(rules)
    }
}
