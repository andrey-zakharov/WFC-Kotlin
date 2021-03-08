package org.mifek.wfc.core

import org.mifek.wfc.heuristics.SelectionHeuristic
import org.mifek.wfc.topologies.Topology
import org.mifek.wfc.utils.EventHandler
import org.mifek.wfc.utils.randomIndex
import kotlin.random.Random

open class WfcAlgorithm(
    protected val topology: Topology,
    protected val weights: DoubleArray,
    protected val propagator: Array<Array<IntArray>>,
    protected val heuristic: SelectionHeuristic,
) {
    protected val patternCount = weights.size

    /**
     * Contains option-based boolean array for each pixel
     */
    protected val wavesArray = Array(topology.totalSize) { BooleanArray(patternCount) }
    val waves = Waves(wavesArray)

    /**
     * Used for banning patterns at pixels, for each pattern on each pixel we remember how many compatible pairs are in
     * each direction. If any of the directions gets to 0, we ban the pattern since it becomes incompatible with that
     * neighbour.
     */
    protected val compatible = Array(wavesArray.size) { Array(patternCount) { IntArray(topology.maxDegree) } }

    /**
     * Help variable for propagate
     */
    protected var stacksize: Int = 0

    /**
     * Pre-initialized stack, there may never be more then waves * patterns elements.
     */
    protected val stack = arrayOfNulls<Pair<Int, Int>?>(wavesArray.size * patternCount)

    // EVENTS

    /**
     * Triggered *after* core initialization but before anything is processed.
     */
    val onStart = EventHandler<WfcAlgorithm>()

    /**
     * Triggered *after* core cleanup.
     */
    val onClear = EventHandler<WfcAlgorithm>()

    /**
     * Triggered *after* wave observation
     */
    val onObserve = EventHandler<Triple<WfcAlgorithm, Int, Int>>()

    /**
     * Triggered *after* core cleanup.
     */
    val onBan = EventHandler<Triple<WfcAlgorithm, Int, Int>>()

    /**
     * Triggered *after* the algorithm fails
     */
    val onFail = EventHandler<WfcAlgorithm>()

    /**
     * Triggered *after* each propagation step (when original stack-size reaches 0)
     */
    val onPropagationStep = EventHandler<WfcAlgorithm>()

    /**
     * Triggered *after* each step of the algorithm (observation + propagation)
     */
    val onStep = EventHandler<WfcAlgorithm>()

    /**
     * Triggered at the end of algorithm run (whatever the result)
     */
    val onFinished = EventHandler<WfcAlgorithm>()

    /**
     * Clears the calculations made in the network
     */
    open fun clear() {
        for (w in wavesArray.indices) {
            for (p in 0 until patternCount) {
                wavesArray[w][p] = true
                for (n in 0 until topology.maxDegree) {
                    compatible[w][p][n] =
                        propagator[(n + topology.maxDegree / 2) % topology.maxDegree][p].size // opposite direction number of same patterns
                }
            }
        }

        onClear(this)
    }

    /**
     * Bans the pattern in selected wave
     */
    open fun ban(wave: Int, pattern: Int): Boolean? {
        if (!wavesArray[wave][pattern]) return false

        wavesArray[wave][pattern] = false

        val compatiblePatterns = compatible[wave][pattern]
        for (neighbour in 0 until topology.maxDegree) compatiblePatterns[neighbour] = 0

        stack[stacksize++] = Pair(wave, pattern)

        onBan(Triple(this, wave, pattern))

        if (!wavesArray[wave].filter { it }.any()) return null

        return true
    }

    protected fun observePatternUsingWeights(patterns: BooleanArray, random: Random = Random.Default): Int {
        val distribution = DoubleArray(patternCount) { if (patterns[it]) weights[it] else 0.0 }
        return distribution.randomIndex(random)
    }

    /**
     * Observes a pattern in a wave using given random
     */
    open fun observe(random: Random = Random.Default): Boolean? {
        val selectedWave = heuristic.select()

        if (selectedWave == null) {
            onFail(this)
            return false
        } else if (selectedWave == -1) {
            return true
        }

        val wavePatterns = wavesArray[selectedWave]
        val observedPattern = observePatternUsingWeights(wavePatterns, random)

        for (patternIndex in 0 until patternCount) {
            if (wavePatterns[patternIndex] != (patternIndex == observedPattern)) {
                if (ban(selectedWave, patternIndex) == null) {
                    onFail(this)
                    return false
                }
            }
        }

        onObserve(Triple(this, selectedWave, observedPattern))

        return null
    }

    /**
     * Forces pattern observation to given index.
     *
     * This function is intended for user-interaction.
     */
    open fun forceObserve(index: Int, pattern: Int): Boolean? {
        val wavePatterns = wavesArray[index]

        if (!wavePatterns[pattern]) return false

        for (patternIndex in 0 until patternCount) {
            if (wavePatterns[patternIndex] != (patternIndex == pattern)) {
                if (ban(index, patternIndex) == null) {
                    return false
                }
            }
        }

        onObserve(Triple(this, index, pattern))

        return null
    }

    /**
     * Propagates consequences of bans
     */
    open fun propagate(): Boolean {
        var original = stacksize
        while (stacksize > 0) {
            // pop item
            stacksize--
            original--
            val actual = stack[stacksize] ?: continue

            // we have banned patternIndex in waveIndex location
            val (waveIndex, patternIndex) = actual
            for (neighbour in topology.neighbourIterator(waveIndex)) {
                val direction = neighbour.first
                val neighbourIndex = neighbour.second
                val neighbourPatterns = propagator[direction][patternIndex]
                val compatibles = compatible[neighbourIndex]

                for (neighbourPatternIndex in 0 until patternCount) {
                    if ((compatibles[neighbourPatternIndex][direction] != 0 || !wavesArray[neighbourIndex][neighbourPatternIndex]) && neighbourPatternIndex !in neighbourPatterns) {
                        continue
                    }
                    val optionCompatible = compatibles[neighbourPatternIndex]
                    if (optionCompatible[direction] > 0) {
                        optionCompatible[direction]--
                    }
                    if (optionCompatible[direction] == 0) {
                        if (ban(neighbourIndex, neighbourPatternIndex) == null) {
                            onFail(this)
                            return false
                        }
                    }
                }
            }

            if (original == 0) {
                original = stacksize
                onPropagationStep(this)
            }
        }
        return true
    }

    /**
     * Performs single step to the WFC
     */
    open fun step(random: Random = Random.Default): Boolean? {
        val result = observe(random)
        if (result != null) return result
        if (!propagate()) {
            return false
        }
        onStep(this)
        return null
    }

    /**
     * Main loop of WFC algorithm
     */
    open fun run(seed: Int = Random.nextInt(), limit: Int = 0, backtrackLimit: Int = 0): Boolean {
        val random = Random(seed)
        heuristic.initialize(this, random)

        clear()

        onStart(this)

        // TODO: Allow backtracking if bigger than 0
        if (limit != 0) {
            var l = 0
            while (l < limit) {
                val result = step(random)
                if (result != null) {
                    onFinished(this)
                    return result
                }
                l++
            }
        } else {
            while (true) {
                val result = step(random)
                if (result != null) {
                    onFinished(this)
                    return result
                }
            }
        }

        onFinished(this)
        return true
    }
}