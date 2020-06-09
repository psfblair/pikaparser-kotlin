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

package net.phobot.parser

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
// import io.kotest.assertions.
import io.kotest.matchers.shouldBe
import net.phobot.parser.TestUtils.loadResourceFile
import net.phobot.parser.grammar.MetaGrammar
import net.phobot.parser.utils.ParserInfo

class EndToEndTest : FunSpec({
    test("Can parse arithmetic") {
        val grammarSpec = loadResourceFile("arithmetic.grammar")
        val grammar = MetaGrammar.parse(grammarSpec)

        val input = loadResourceFile("arithmetic.input")
        val memoTable = grammar.parse(input)

        val topRuleName = "Program"
        val recoveryRuleNames = arrayOf(topRuleName, "Statement")

        ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false)

        val allClauses = memoTable.grammar.allClauses
        allClauses.size shouldBe(27)

        val firstClause = allClauses.get(0)
        var matches = memoTable.getAllMatches(firstClause)
        matches.size shouldBe(16)

        val firstMatch = matches.get(0)
        firstMatch.length shouldBe(1)
        firstMatch.memoKey.startPos shouldBe(0)
        firstMatch.memoKey.toStringWithRuleNames() shouldBe("[a-z] : 0")

        val sixteenthMatch = matches.get(15)
        sixteenthMatch.length shouldBe(1)
        sixteenthMatch.memoKey.startPos shouldBe(21)
        sixteenthMatch.memoKey.toStringWithRuleNames() shouldBe("[a-z] : 21")

        val lastClause = allClauses.get(26)
        matches = memoTable.getAllMatches(lastClause)

        val topLevelMatch = matches.get(0)
        topLevelMatch.length shouldBe(23)
        topLevelMatch.memoKey.startPos shouldBe(0)
        topLevelMatch.toStringWithRuleNames() shouldBe("Program <- Statement+ : 0+23")

        topLevelMatch.getSubClauseMatches().size shouldBe(1)

        val firstSubclauseMatchOfLastMatch = topLevelMatch.getSubClauseMatches().get(0)
        firstSubclauseMatchOfLastMatch.key.shouldBeNull()

        val subClauseString = firstSubclauseMatchOfLastMatch.value.toStringWithRuleNames()
        subClauseString shouldBe("Statement <- var:[a-z]+ '=' E ';' : 0+23")
    }

    test("Can parse Java") {
        val grammarSpec = loadResourceFile("Java.1.8.peg")
        val grammar = MetaGrammar.parse(grammarSpec)

        val input = loadResourceFile("MemoTable.java")
        val memoTable = grammar.parse(input)

        val topRuleName = "Compilation"
        val recoveryRuleNames = arrayOf(topRuleName, "CompilationUnit")

        // Huge output; only do this if you have a big buffer
        // ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false)

        val syntaxErrors = memoTable.getSyntaxErrors(recoveryRuleNames)
        if (! syntaxErrors.isEmpty()) {
            ParserInfo.printSyntaxErrors(syntaxErrors);
        }
   }
/*
    test("Can parse C") {
        val grammarSpec = loadResourceFile("C.peg")
        val grammar = MetaGrammar.parse(grammarSpec)

        val input = loadResourceFile("libristretto.i")
        val memoTable = grammar.parse(input)

        val topRuleName = "Program"
        val recoveryRuleNames = arrayOf(topRuleName, "Statement")

        ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false)
    }
*/
})