package com.scrollcapture.capture

import kotlin.math.max
import kotlin.math.min

object TextUtils {

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    fun levenshteinDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val lenA = a.length
        val lenB = b.length

        // Use single-row DP for space efficiency
        var prev = IntArray(lenB + 1) { it }
        var curr = IntArray(lenB + 1)

        for (i in 1..lenA) {
            curr[0] = i
            for (j in 1..lenB) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[lenB]
    }

    /**
     * Normalized similarity between two strings (0.0 = completely different, 1.0 = identical).
     */
    fun normalizedSimilarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshteinDistance(a, b).toDouble() / maxLen
    }

    /**
     * Normalize a line for comparison: trim, collapse whitespace, lowercase.
     */
    fun normalizeLine(line: String): String {
        return line.trim().replace(Regex("\\s+"), " ").lowercase()
    }
}
