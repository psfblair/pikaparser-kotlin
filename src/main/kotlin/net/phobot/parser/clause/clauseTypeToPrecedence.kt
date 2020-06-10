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

package net.phobot.parser.clause

import net.phobot.parser.clause.aux.ASTNodeLabel
import net.phobot.parser.clause.aux.RuleRef
import net.phobot.parser.clause.nonterminal.*
import net.phobot.parser.clause.terminal.Terminal

// GrammarPrecedenceLevels levels (should correspond to levels in the grammar):
val clauseTypeToPrecedence =
        mapOf(
           Terminal::class to      7,
           // Treat RuleRef as having the same precedence as a terminal for string interning purposes
           RuleRef::class to       7,
           OneOrMore::class to     6,
           // ZeroOrMore is not present in the final grammar, so it is skipped here
           NotFollowedBy::class to 5,
           FollowedBy::class to    5,
           // Optional is not present in final grammar, so it is skipped here
           ASTNodeLabel::class to  3,
           Seq::class to           2,
           First::class to         1
        )
