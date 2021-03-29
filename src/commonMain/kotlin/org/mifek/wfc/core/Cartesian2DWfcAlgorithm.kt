package org.mifek.wfc.core

import org.mifek.wfc.datastructures.IntArray2D
import org.mifek.wfc.heuristics.LowestEntropyHeuristic
import org.mifek.wfc.models.Patterns
import org.mifek.wfc.models.Pixels
import org.mifek.wfc.topologies.Cartesian2DTopology

open class Cartesian2DWfcAlgorithm(
    private val topology2D: Cartesian2DTopology,
    weights: DoubleArray,
    propagator: Array<Array<IntArray>>,
    private val patterns: Patterns,
    private val pixels: Pixels,
) : WfcAlgorithm(
    topology2D,
    weights,
    propagator,
    LowestEntropyHeuristic(patterns.size, topology2D.totalSize, weights)
) {
    /**
     * Bans all patterns that do not contain given pixel
     */
    private fun setPixel(waveIndex: Int, pixel: Int) {
        (0 until patterns.size).minus(pixels[pixel]).forEach {
            ban(waveIndex, it)
        }
        propagate()
    }

    /**
     * Bans all patterns that do not contain any of given pixels
     */
    private fun setPixels(waveIndex: Int, pixels: Iterable<Int>) {
        (0 until patterns.size)
            .minus(
                pixels
                    .map { this.pixels[it] }
                    .reduce { acc: Sequence<Int>, sequence: Sequence<Int> -> acc.plus(sequence) }
            )
            .forEach {
                ban(waveIndex, it)
            }
        propagate()
    }

    private fun setPatterns(waveIndex: Int, patterns: Iterable<Int>) {
        (0 until this.patterns.size)
            .minus(patterns)
            .forEach {
                ban(waveIndex, it)
            }
        propagate()
    }

    private fun banPatterns(waveIndex: Int, patterns: Iterable<Int>) {
        patterns.forEach {
            ban(waveIndex, it)
        }
        propagate()
    }

    /**
     * Bans all patterns that do not contain any of given pixels
     */
    private fun setPixels(waveIndices: Iterable<Int>, pixels: Iterable<Int>) {
        (0 until patterns.size)
            .minus(
                pixels
                    .map { this.pixels[it] }
                    .reduce { acc: Sequence<Int>, sequence: Sequence<Int> -> acc.plus(sequence) }
            )
            .forEach { pattern ->
                waveIndices.forEach {
                    ban(it, pattern)
                }
            }
        propagate()
    }

    private fun setPatterns(waveIndices: Iterable<Int>, patterns: Iterable<Int>) {
        (0 until this.patterns.size)
            .minus(patterns)
            .forEach { pattern ->
                waveIndices.forEach {
                    ban(it, pattern)
                }
            }
        propagate()
    }

    private fun banPatterns(waveIndices: Iterable<Int>, patterns: Iterable<Int>) {
        patterns.forEach { pattern ->
            waveIndices.forEach {
                ban(it, pattern)
            }
        }
        propagate()
    }

    /**
     * Bans all patterns that do not contain given pixel
     */
    fun setPixel(x: Int, y: Int, pixel: Int) {
        this.setPixel(x + y * topology2D.width, pixel)
    }

    /**
     * Bans all patterns that do not contain any of given pixels
     */
    fun setPixels(x: Int, y: Int, pixels: Iterable<Int>) {
        this.setPixels(x + y * topology2D.width, pixels)
    }

    /**
     * Bans all patterns that do not contain any of given pixels
     */
    fun setMultiplePixels(coords: Iterable<Pair<Int, Int>>, pixels: Iterable<Int>) {
        this.setPixels(coords.map { pair -> pair.first + pair.second * topology2D.width }, pixels)
    }

    /**
     * Bans other than given patterns
     */
    fun setPatterns(x: Int, y: Int, patterns: Iterable<Int>) {
        this.setPatterns(x + y * topology2D.width, patterns)
    }

    /**
     * Bans given patterns
     */
    fun banPatterns(x: Int, y: Int, patterns: Iterable<Int>) {
        this.setPatterns(x + y * topology2D.width, patterns)
    }

    /**
     * Bans other than given patterns
     */
    fun setMultiplePatterns(coords: Iterable<Pair<Int, Int>>, patterns: Iterable<Int>) {
        this.setPatterns(coords.map { pair -> pair.first + pair.second * topology2D.width }, patterns)
    }

    /**
     * Bans given patterns
     */
    fun banMultiplePatterns(coords: Iterable<Pair<Int, Int>>, patterns: Iterable<Int>) {
        this.banPatterns(coords.map { pair -> pair.first + pair.second * topology2D.width }, patterns)
    }

    /**
     * Constructs output from a wave for overlapping model, returns averages when multiple patterns available
     */
    open fun constructOutput(): IntArray2D {
        return IntArray2D(topology2D.width, topology2D.height) { waveIndex ->
            val a = 0
            val b = 1
            val sum = waves[waveIndex].sumOf {
                when (it) {
                    false -> a
                    true -> b
                }
            }
            when (sum) {
                0 -> -123456789
                1 -> patterns.pixels[patterns.pixels.indices.filter { waves[waveIndex, it] }[0]]
                else -> {
                    patterns.indices
                        .filter { waves[waveIndex, it] }
                        .map { patterns.pixels[it] }
                        .sum() / sum
                }
            }
        }
    }
}