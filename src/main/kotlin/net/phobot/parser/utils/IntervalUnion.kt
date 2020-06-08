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

package net.phobot.parser.utils

import java.util.*
import kotlin.math.max
import kotlin.math.min

/** Grammar utils.  */
class IntervalUnion {
    /** Return all the nonoverlapping ranges in this interval union.  */
    val nonOverlappingRanges: NavigableMap<Int, Int> = TreeMap()

    /** Add a range to a union of ranges.  */
    fun addRange(startPos: Int, endPos: Int) {
        require(endPos >= startPos) { "endPos < startPos" }
        // Try merging new range with floor entry in TreeMap
        val floorEntry = nonOverlappingRanges.floorEntry(startPos)
        val floorEntryStart = floorEntry?.key
        val floorEntryEnd = floorEntry?.value
        val newEntryRangeStart: Int
        val newEntryRangeEnd: Int
        if (floorEntryStart == null || floorEntryEnd == null || floorEntryEnd < startPos) {
            // There is no startFloorEntry, or startFloorEntry ends before startPos -- add a new entry
            newEntryRangeStart = startPos
            newEntryRangeEnd = endPos
        } else {
            // startFloorEntry overlaps with range -- extend startFloorEntry
            newEntryRangeStart = floorEntryStart.toInt()
            newEntryRangeEnd = max(floorEntryEnd, endPos)
        }

        // Try merging new range with the following entry in TreeMap
        val higherEntry = nonOverlappingRanges.higherEntry(newEntryRangeStart)
        val higherEntryStart = higherEntry?.key
        val higherEntryEnd = higherEntry?.value
        if (higherEntryStart != null && higherEntryEnd != null && higherEntryStart <= newEntryRangeEnd) {
            // Expanded-range entry overlaps with the following entry -- collapse them into one
            nonOverlappingRanges.remove(higherEntryStart)
            val expandedRangeEnd = max(newEntryRangeEnd, higherEntryEnd)
            nonOverlappingRanges[newEntryRangeStart] = expandedRangeEnd
        } else {
            // There's no overlap, just add the new entry (may overwrite the earlier entry for the range start)
            nonOverlappingRanges[newEntryRangeStart] = newEntryRangeEnd
        }
    }

    /** Get the inverse of the intervals in this set within [StartPos, endPos).  */
    fun invert(startPos: Int, endPos: Int): IntervalUnion {
        val invertedIntervalSet = IntervalUnion()

        var prevEndPos = startPos
        for (ent in nonOverlappingRanges.entries) {
            val currStartPos = ent.key
            if (currStartPos > endPos) {
                break
            }
            val currEndPos = ent.value
            if (currStartPos > prevEndPos) {
                // There's a gap of at least one position between adjacent ranges
                invertedIntervalSet.addRange(prevEndPos, currStartPos)
            }
            prevEndPos = currEndPos.toInt()
        }
        if (!nonOverlappingRanges.isEmpty()) {
            val lastEnt = nonOverlappingRanges.lastEntry()
            val lastEntEndPos = lastEnt.value
            if (lastEntEndPos < endPos) {
                // Final range: there is at least one position before endPos
                invertedIntervalSet.addRange(lastEntEndPos, endPos)
            }
        }
        return invertedIntervalSet
    }

    /** Return true if the specified range overlaps with any range in this interval union.  */
    fun rangeOverlaps(startPos: Int, endPos: Int): Boolean {
        // Range overlap test: https://stackoverflow.com/a/25369187/3950982
        // (Need to repeat for both floor entry and ceiling entry)
        val floorEntry = nonOverlappingRanges.floorEntry(startPos)
        if (floorEntry != null) {
            val floorEntryStart = floorEntry.key
            val floorEntryEnd = floorEntry.key

            val spanBetweenExtremes = max(endPos, floorEntryEnd) - min(startPos, floorEntryStart)
            val sumOfTheLengths = (endPos - startPos) + (floorEntryEnd - floorEntryStart)

            if (spanBetweenExtremes < sumOfTheLengths) {
                return true
            }
        }

        val ceilEntry = nonOverlappingRanges.ceilingEntry(startPos)
        if (ceilEntry != null) {
            val ceilEntryStart = ceilEntry.key
            val ceilEntryEnd = ceilEntry.key

            val spanBetweenExtremes = max(endPos, ceilEntryEnd) - min(startPos, ceilEntryStart)
            val sumOfTheLengths = (endPos - startPos) + (ceilEntryEnd - ceilEntryStart)

            if (spanBetweenExtremes < sumOfTheLengths) {
                return true
            }
        }
        return false
    }
}
