package com.github.devvikassoni.leaklens.verification

object GoldenComparator {

    fun compare(actual: NormalizedLeak, expected: NormalizedLeak): ComparisonResult {
        val diffs = mutableListOf<String>()

        if (actual.className != expected.className) {
            diffs.add("Class mismatch: Expected ${expected.className}, Actual ${actual.className}")
        }

        if (actual.referenceChain != expected.referenceChain) {
            diffs.add("Reference chain mismatch:")
            diffs.add("  Expected: ${expected.referenceChain.joinToString(" -> ")}")
            diffs.add("  Actual:   ${actual.referenceChain.joinToString(" -> ")}")
        }

        return ComparisonResult(
            scenarioId = "unknown", // Should be set by caller
            isMatch = diffs.isEmpty(),
            differences = diffs
        )
    }
}
