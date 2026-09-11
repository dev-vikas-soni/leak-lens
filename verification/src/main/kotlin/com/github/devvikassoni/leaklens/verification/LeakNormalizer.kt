package com.github.devvikassoni.leaklens.verification

import shark.HeapAnalysisSuccess
import shark.LeakTrace

object LeakNormalizer {

    /**
     * Normalizes a Shark analysis result into a deterministic format for comparison.
     */
    fun normalize(analysis: HeapAnalysisSuccess): List<NormalizedLeak> {
        val allLeaks = analysis.applicationLeaks + analysis.libraryLeaks
        return allLeaks.map { leak ->
            val trace = leak.leakTraces.first()
            NormalizedLeak(
                className = trace.leakingObject.className,
                severity = if (leak is shark.LibraryLeak) "LIBRARY_LEAK" else "CRITICAL",
                referenceChain = normalizeReferenceChain(trace),
                leakingReasons = extractLeakingReasons(trace)
            )
        }
    }

    private fun normalizeReferenceChain(trace: LeakTrace): List<String> {
        return trace.referencePath.mapNotNull { reference ->
            val owningClass = reference.originObject.className.substringAfterLast(".")
            val referenceName = reference.referenceName

            when {
                referenceName == "INSTANCE" -> null // Skip Kotlin object instance field
                referenceName.isEmpty() -> owningClass
                referenceName.startsWith("[") -> owningClass
                referenceName == "this$0" -> owningClass
                else -> "$owningClass.$referenceName"
            }
        }.distinct()
    }

    private fun extractLeakingReasons(trace: LeakTrace): List<String> {
        val reasons = mutableListOf<String>()
        reasons.addAll(trace.leakingObject.labels)
        if (trace.leakingObject.leakingStatusReason.isNotEmpty()) {
            reasons.add(trace.leakingObject.leakingStatusReason)
        }
        for (reference in trace.referencePath) {
            reasons.addAll(reference.originObject.labels)
            if (reference.originObject.leakingStatusReason.isNotEmpty()) {
                reasons.add(reference.originObject.leakingStatusReason)
            }
        }
        return reasons.distinct().filter { it.isNotBlank() }.sorted()
    }
}
