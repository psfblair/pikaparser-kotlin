package net.phobot.parser.grammar

import net.phobot.parser.ast.ASTNode
import net.phobot.parser.ast.IDENT_AST
import net.phobot.parser.ast.LABEL_AST
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
import net.phobot.parser.clause.ClauseFactory
import net.phobot.parser.clause.ClauseFactory.str
import net.phobot.parser.clause.ClauseFactory.charRangeClause
import net.phobot.parser.clause.aux.ASTNodeLabel
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.nonterminal.First
import net.phobot.parser.clause.nonterminal.Seq
import net.phobot.parser.clause.terminal.CharSet
import net.phobot.parser.clause.terminal.Nothing
import net.phobot.parser.clause.terminal.Start
import net.phobot.parser.utils.StringUtils
import kotlin.streams.toList

object AstNodeParser {

    private enum class SingleChildClauseType(val typeName: String, val clauseConstructor: (Clause) -> Clause) {
        ONE_OR_MORE        ("OneOrMore", ClauseFactory::oneOrMore),
        ZERO_OR_MORE       ("ZeroOrMore", ClauseFactory::zeroOrMore),
        OPTIONAL           ("Optional", ClauseFactory::optional),
        FOLLOWED_BY        ("FollowedBy", ClauseFactory::followedBy),
        NOT_FOLLOWED_BY    ("NotFollowedBy", ClauseFactory::notFollowedBy)
    }

    private enum class MultiChildClauseType(val typeName: String, val clauseConstructor: (Array<Clause>) -> Clause) {
        SEQ   ("Seq", ::Seq),
        FIRST ("First", ::First)
    }

    /** Recursively parse a single AST node.  */
    fun parseASTNode(astNode: ASTNode): Clause {
        val clause: Clause
        when (astNode.label) {
            SEQ_AST                 -> clause = multiChildClause(astNode, MultiChildClauseType.SEQ)
            FIRST_AST               -> clause = multiChildClause(astNode, MultiChildClauseType.FIRST)
            ONE_OR_MORE_AST         -> clause = singleChildClause(astNode, SingleChildClauseType.ONE_OR_MORE)
            ZERO_OR_MORE_AST        -> clause = singleChildClause(astNode, SingleChildClauseType.ZERO_OR_MORE)
            OPTIONAL_AST            -> clause = singleChildClause(astNode, SingleChildClauseType.OPTIONAL)
            FOLLOWED_BY_AST         -> clause = singleChildClause(astNode, SingleChildClauseType.FOLLOWED_BY)
            NOT_FOLLOWED_BY_AST     -> clause = singleChildClause(astNode, SingleChildClauseType.NOT_FOLLOWED_BY)
            LABEL_AST               -> clause = ASTNodeLabel(astNode.firstChild.text, parseASTNode(astNode.secondChild.firstChild))
            IDENT_AST               -> clause = RuleRef(astNode.text) // Rule name ref
            QUOTED_STRING_AST           // Doesn't include surrounding quotes
                                    -> clause = str(StringUtils.unescapeString(astNode.text))
            SINGLE_QUOTED_CHAR_AST  -> clause = CharSet(StringUtils.unescapeChar(astNode.text))
            START_AST               -> clause = Start()
            NOTHING_AST             -> clause = Nothing()
            CHAR_RANGE_AST          -> clause = charRangeClause(astNode.text)
        else                            // Keep recursing for parens (the only type of AST node that doesn't have a label)
                                    -> clause = singleChild(astNode, typeName = "node")
        }
        return clause
    }

    /** Recursively convert a list of AST nodes into a list of Clauses. */
    private fun parseASTNodes(astNodes: List<ASTNode>): List<Clause> {
        return astNodes
                .stream()
                .map { node -> parseASTNode(node)}.toList()
    }

    private fun singleChild(astNode: ASTNode, typeName: String): Clause {
        val childAstNodes = parseASTNodes(astNode.children)
        val size = childAstNodes.size
        require(size == 1) { "Expected one subclause for ${typeName}, got $size: ${astNode}"}
        return childAstNodes[0]
    }

    private fun singleChildClause(astNode: ASTNode, clauseType: SingleChildClauseType): Clause {
        val childASTNode = singleChild(astNode, clauseType.typeName)
        val clauseConstructor = clauseType.clauseConstructor
        return clauseConstructor(childASTNode)
    }

    private fun multiChildClause(astNode: ASTNode, clauseType: MultiChildClauseType): Clause {
        val childASTNodes = parseASTNodes(astNode.children).toTypedArray()
        val size = childASTNodes.size
        require (size > 1) { "Must have at least 2 child AST nodes for ${clauseType.typeName}, got ${size}: ${astNode}" }

        val clauseConstructor = clauseType.clauseConstructor
        return clauseConstructor(childASTNodes)
    }
}