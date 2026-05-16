package com.scrollcapture.capture

/**
 * Intelligent text assembly engine that deduplicates overlapping OCR frames.
 *
 * Strategy:
 * 1. Compare each new frame's top lines against the previous frame's bottom lines
 *    using fuzzy matching (normalized Levenshtein similarity ≥ 0.80).
 * 2. Find the overlap boundary — discard the duplicate top portion of the new frame.
 * 3. Append only truly new lines.
 * 4. Track line frequency of APPENDED lines only (not raw frame lines) to detect
 *    persistent UI chrome and exclude them from output.
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

        if (previousFrameLines.isEmpty()) {
            // First frame — add all lines
            assembledLines.addAll(newLines)
            // Bug 1 fix: count frequency only for appended lines, not all raw frame lines
            newLines.forEach { line ->
                val normalized = TextUtils.normalizeLine(line)
                lineFrequency[normalized] = (lineFrequency[normalized] ?: 0) + 1
            }
            previousFrameLines = newLines
            return getAssembledText()
        }

        // Find overlap between previous frame's tail and new frame's head
        val overlapIndex = findOverlapIndex(previousFrameLines, newLines)

        // Append only the new (non-overlapping) lines
        if (overlapIndex < newLines.size) {
            val newContent = newLines.subList(overlapIndex, newLines.size)
            // Bug 1 fix: track frequency only on lines we actually append
            newContent.forEach { line ->
                val normalized = TextUtils.normalizeLine(line)
                lineFrequency[normalized] = (lineFrequency[normalized] ?: 0) + 1
            }
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

        val prevTail = prevLines.takeLast(minOf(prevLines.size, 20))
        val normalizedPrevTail = prevTail.map { TextUtils.normalizeLine(it) }

        // Find the first line in newLines that matches any line in prevTail
        var firstMatchInNew = -1
        var matchedPrevIndex = -1

        for (i in newLines.indices) {
            val normalizedNew = TextUtils.normalizeLine(newLines[i])
            if (normalizedNew.length < 3) continue

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
            // No overlap found — big scroll jump, append everything
            return 0
        }

        // Bug 4 fix: the match at firstMatchInNew aligns with prevTail[matchedPrevIndex].
        // Everything from matchedPrevIndex onward in prevTail was already captured.
        // So the remaining new content starts after those already-seen prevTail lines.
        val remainingPrevTailLines = normalizedPrevTail.size - matchedPrevIndex

        // Verify the overlap is consistent: walk forward from the match point
        var overlapEnd = firstMatchInNew
        var prevIdx = matchedPrevIndex
        var matchedCount = 0

        while (overlapEnd < newLines.size && prevIdx < normalizedPrevTail.size) {
            val simScore = TextUtils.normalizedSimilarity(
                TextUtils.normalizeLine(newLines[overlapEnd]),
                normalizedPrevTail[prevIdx]
            )
            if (simScore < similarityThreshold) break
            overlapEnd++
            prevIdx++
            matchedCount++
        }

        // Bug 3 fix: if confidence is low, skip the whole frame rather than appending everything
        if (matchedCount < 2 && newLines.size > 5) {
            return newLines.size  // Add nothing — not confident enough
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

    fun getFrameCount(): Int = totalFrames
    fun getCharCount(): Int = getAssembledText().length
    fun getRawLineCount(): Int = assembledLines.size

    fun reset() {
        assembledLines.clear()
        previousFrameLines = emptyList()
        lineFrequency.clear()
        totalFrames = 0
    }
}