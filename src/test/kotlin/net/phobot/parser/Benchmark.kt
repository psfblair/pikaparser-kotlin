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

package net.phobot.parser

// import io.kotest.assertions.
import io.kotest.core.spec.style.FunSpec
import net.phobot.parser.Benchmark.Benchmark.executeInTimedLoop
import net.phobot.parser.TestUtils.loadResourceFile
import net.phobot.parser.grammar.MetaGrammar
import net.phobot.parser.memotable.MemoTable
import java.util.*


public class Benchmark : FunSpec({
    test("arithmetic benchmark") {
        val grammarSpec = loadResourceFile("arithmetic.grammar")
        val toBeParsed = loadResourceFile("arithmetic.input")

        val parseGrammarAndParseInput = { input: String ->
            val grammar = MetaGrammar.parse(grammarSpec)
            grammar.parse(input)
        }

        executeInTimedLoop(parseGrammarAndParseInput, toBeParsed, "arithmetic")
    }
    test("java grammar benchmark") {
        val grammarSpec = loadResourceFile("Java.1.8.peg")

        executeInTimedLoop( { input: String -> MetaGrammar.parse(input) }, grammarSpec, "java-grammar")
    }

    test("java parse benchmark") {
        val grammarSpec = loadResourceFile("Java.1.8.peg")
        val toBeParsed = loadResourceFile("MemoTable.java")

        val grammar = MetaGrammar.parse(grammarSpec)

        executeInTimedLoop({ input: String -> grammar.parse(input) }, toBeParsed, "java-parse")
    }
}) {

    object Benchmark {

        fun <T> executeInTimedLoop(toExecute: (String) -> T, input: String, benchmarkName: String) : Unit {
            val results = LongArray(100)
            (0..99).forEach { i ->
                val start = System.nanoTime()
                toExecute(input)
                results[i] = System.nanoTime() - start
            }
            println("\n\n\n===================== RESULTS FOR $benchmarkName=====================")
            println(
                    Arrays.stream(results)
                            .mapToDouble { nano: Long -> nano / 1000000000.0 }
                            .summaryStatistics()
            )
        }
    }
}

/*
MacBook Air (Early 2015), 2.2 GHz Intel Core i7, 8 GB 1600 MHz DDR3 RAM
macOS 10.13.6
IntelliJ IDEA 2020.1
Results in seconds
===================== RESULTS FOR arithmetic=====================
DoubleSummaryStatistics{count=100, sum=1.394441, min=0.004973, average=0.013944, max=0.252265}

===================== RESULTS FOR java-grammar=====================
DoubleSummaryStatistics{count=100, sum=63.733993, min=0.499645, average=0.637340, max=1.354979}

===================== RESULTS FOR java-parse=====================
DoubleSummaryStatistics{count=100, sum=103.694866, min=0.826100, average=1.036949, max=1.479693}
 */