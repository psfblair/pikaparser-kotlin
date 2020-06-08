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
// import io.kotest.assertions.
import io.kotest.matchers.shouldBe
import net.phobot.parser.grammar.MetaGrammar
import net.phobot.parser.utils.ParserInfo
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.jvm.javaClass

class EndToEndTest : FunSpec({
    fun loadResourceFile(filename:String): String {
        val resourceUrl = javaClass.getClassLoader().getResource(filename)!!.toURI()
        return Files.readAllLines(Paths.get(resourceUrl)).joinToString()
    }
    
    test("Can parse arithmetic") {
        val grammarSpec = loadResourceFile("arithmetic.grammar")
        val grammar = MetaGrammar.parse(grammarSpec)

        val input = loadResourceFile("arithmetic.input")
        val memoTable = grammar.parse(input)

        val topRuleName = "Program"
        val recoveryRuleNames = arrayOf(topRuleName, "Statement")

        ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false)
    }

    test("Can parse Java") {
        val grammarSpec = loadResourceFile("Java.1.8.peg")
        val grammar = MetaGrammar.parse(grammarSpec)

        val input = loadResourceFile("MemoTable.java")
        val memoTable = grammar.parse(input)

        val topRuleName = "Program"
        val recoveryRuleNames = arrayOf(topRuleName, "Statement")

        ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false)
    }

    test("Can parse C") {
        val grammarSpec = loadResourceFile("C.peg")
        val grammar = MetaGrammar.parse(grammarSpec)

        val input = loadResourceFile("libristretto.i")
        val memoTable = grammar.parse(input)

        val topRuleName = "Program"
        val recoveryRuleNames = arrayOf(topRuleName, "Statement")

        ParserInfo.printParseResult(topRuleName, memoTable, recoveryRuleNames, false)
    }
})