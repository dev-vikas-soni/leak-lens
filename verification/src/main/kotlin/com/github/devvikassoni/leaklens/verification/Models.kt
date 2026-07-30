package com.github.devvikassoni.leaklens.verification

data class GoldenReport(
    val schemaVersion: Int,
    val metadata: Metadata,
    val scenarios: List<ScenarioExpectation>
)

data class Metadata(
    val leakLensVersion: String,
    val sharkVersion: String,
    val kotlinVersion: String
)

data class ScenarioExpectation(
    val id: String,
    val expectedLeak: NormalizedLeak
)

data class NormalizedLeak(
    val className: String,
    val severity: String,
    val referenceChain: List<String>,
    val inspectionId: String? = null
)

data class ComparisonResult(
    val scenarioId: String,
    val isMatch: Boolean,
    val differences: List<String>
)
