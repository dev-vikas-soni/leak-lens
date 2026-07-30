package com.github.devvikassoni.leaklens.sample.core.common

/**
 * Metadata for a deterministic memory leak scenario.
 */
interface LeakScenario {
    val id: String
    val title: String
    val description: String
    val leakSource: String        // Field/Method where the leak originates
    val expectedInspection: String // Rule ID that UAST should flag
    val reproductionSteps: String
    val fixSuggestion: String
}
