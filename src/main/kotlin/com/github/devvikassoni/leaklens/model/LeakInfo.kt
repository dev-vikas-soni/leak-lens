package com.github.devvikassoni.leaklens.model

data class LeakInfo(
    val signature: String,
    val shortDescription: String,
    val leakTrace: String,
    val retainedObjectClassName: String,
    val retainedByteSize: Long,
    val retainedObjectCount: Int,
    val severity: LeakSeverity,
    val referenceChain: List<LeakTraceReference>,
    val timestamp: Long = System.currentTimeMillis(),
    val isLibraryLeak: Boolean = false,
    val suggestedFix: String? = null
)

data class LeakTraceReference(
    val owningClassName: String,
    val referenceName: String,
    val referenceType: String,
    val declaredClassName: String? = null,
    val sourceFile: String? = null,
    val lineNumber: Int? = null
)

