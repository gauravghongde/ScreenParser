package com.scrollcapture.capture

/**
 * Intelligent text assembly engine that deduplicates overlapping OCR frames.
 *
 * Strategy:
 * 1. Compare each new frame's top lines against the previous frame's bottom lines
 *    using fuzzy matching (normalized Levenshtein similarity ≥ 0.80).
 * 2. Find the overlap boundary — discard the duplicate top portion of the new frame.
 * 3. Append only truly new lines.
 * 4. Track line frequency across all frames to detect persistent UI chrome
 *    (lines appearing in > 80% of frames) and exclude them from output.
 */
class DeduplicationEngine {

    private val assembledLines = mutableListOf<String>()
    private var previousFrameLines: List<String> = emptyList()
    private val lineFrequency = mutableMapOf<String, Int>()
    private var totalFrames = 0

    private val similarityThreshold = 0.80
    private val chromeThresholdPercent = 0.80

    /**
     * Process a new OCR frame. Returns the current assembled text.
     */
    fun processFrame(newLines: List<String>): String {
        if (newLines.isEmpty()) return getAssembledText()

        totalFrames++

        // Track line frequency for chrome detection
        val normalizedNewLines = newLines.map { TextUtils.normalizeLine(it) }
        normalizedNewLines.toSet().forEach { normalized ->
            lineFrequency[normalized] = (lineFrequency[normalized] ?: 0) + 1
        }

        if (previousFrameLines.isEmpty()) {
            // First frame — add all lines
            assembledLines.addAll(newLines)
            previousFrameLines = newLines
            return getAssembledText()
        }

        // Find overlap between previous frame's tail and new frame's head
        val overlapIndex = findOverlapIndex(previousFrameLines, newLines)

        // Append only the new (non-overlapping) lines
        if (overlapIndex < newLines.size) {
            val newContent = newLines.subList(overlapIndex, newLines.size)
            assembledLines.addAll(newContent)
        }

        previousFrameLines = newLines
        return getAssembledText()
    }

    /**
     * Find the index in newLines where truly new content begins.
     * Lines before this index overlap with the previous frame.
     */
    private fun findOverlapIndex(prevLines: List<String>, newLines: List<String>): Int {
        if (prevLines.isEmpty() || newLines.isEmpty()) return 0

        // Try to find where newLines start matching prevLines
        // We look for the first line in newLines that matches a line in prevLines's tail
        val prevTail = prevLines.takeLast(minOf(prevLines.size, 20))
        val normalizedPrevTail = prevTail.map { TextUtils.normalizeLine(it) }

        // Find the first line in newLines that matches any line in prevTail
        var firstMatchInNew = -1
        var matchedPrevIndex = -1

        for (i in newLines.indices) {
            val normalizedNew = TextUtils.normalizeLine(newLines[i])
            if (normalizedNew.length < 3) continue // skip very short lines

            for (j in normalizedPrevTail.indices) {
                if (TextUtils.normalizedSimilarity(normalizedNew, normalizedPrevTail[j]) >= similarityThreshold) {
                    firstMatchInNew = i
                    matchedPrevIndex = j
                    break
                }
            }
            if (firstMatchInNew >= 0) break
        }

        if (firstMatchInNew < 0) {
            // No overlap found — could be a big scroll jump
            return 0
        }

        // Now verify the overlap is consistent: walk forward from the match point
        var overlapEnd = firstMatchInNew
        var prevIdx = matchedPrevIndex
        while (overlapEnd < newLines.size && prevIdx < normalizedPrevTail.size) {
            val simScore = TextUtils.normalizedSimilarity(
                TextUtils.normalizeLine(newLines[overlapEnd]),
                normalizedPrevTail[prevIdx]
            )
            if (simScore < similarityThreshold) break
            overlapEnd++
            prevIdx++
        }

        // If we only matched 1 line out of context, it might be a false positive
        val matchedCount = overlapEnd - firstMatchInNew
        if (matchedCount < 2 && newLines.size > 5) {
            // Require at least 2 consecutive matching lines for confidence
            return 0
        }

        return overlapEnd
    }

    /**
     * Get the assembled text, filtering out UI chrome noise.
     */
    fun getAssembledText(): String {
        if (totalFrames < 3) {
            // Not enough frames to detect chrome reliably
            return assembledLines.joinToString("\n")
        }

        val chromeThreshold = (totalFrames * chromeThresholdPercent).toInt()
        val chromeLines = lineFrequency
            .filter { it.value >= chromeThreshold }
            .keys

        return assembledLines
            .filter { TextUtils.normalizeLine(it) !in chromeLines }
            .joinToString("\n")
    }

    /**
     * Get raw stats about the current session.
     */
    fun getFrameCount(): Int = totalFrames
    fun getCharCount(): Int = getAssembledText().length
    fun getRawLineCount(): Int = assembledLines.size

    /**
     * Reset for a new session.
     */
    fun reset() {
        assembledLines.clear()
        previousFrameLines = emptyList()
        lineFrequency.clear()
        totalFrames = 0
    }
}
