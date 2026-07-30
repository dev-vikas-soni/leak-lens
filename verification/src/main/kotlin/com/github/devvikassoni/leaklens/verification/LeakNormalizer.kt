package com.github.devvikassoni.leaklens.verification

import shark.HeapAnalysisSuccess
import shark.LeakTrace

object LeakNormalizer {

    /**
     * Normalizes a Shark analysis result into a deterministic format for comparison.
     */
    fun normalize(analysis: HeapAnalysisSuccess): List<NormalizedLeak> {
        return analysis.applicationLeaks.map { leak ->
            val trace = leak.leakTraces.first()
            NormalizedLeak(
                className = trace.leakingObject.className,
                severity = "CRITICAL", // Simplified for verification
                referenceChain = normalizeReferenceChain(trace)
            )
        }
    }

    private fun normalizeReferenceChain(trace: LeakTrace): List<String> {
        return trace.referencePath.map { reference ->
            val owningClass = reference.originObject.className.substringAfterLast(".")
            val referenceName = reference.referenceName
            "$owningClass.$referenceName"
        }
    }
}
