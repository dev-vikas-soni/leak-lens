package com.github.devvikassoni.leaklens.model

data class AnalysisHistoryEntry(
    val timestamp: Long,
    val sourceName: String,
    val leakCount: Int,
    val criticalCount: Int,
    val warningCount: Int,
    val libraryLeakCount: Int,
    val leaks: List<LeakInfo>
)

