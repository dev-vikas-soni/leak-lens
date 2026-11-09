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
    val suggestedFix: String? = null,
    /** Static analysis only: absolute path of the file containing the finding. */
    val sourceFile: String? = null,
    /** Static analysis only: 1-based line of the finding, or 0 when unknown. */
    val sourceLine: Int = 0,
    /** Static analysis only: short name of the inspection that produced the finding. */
    val inspectionId: String? = null
)

data class LeakTraceReference(
    val owningClassName: String,
    val referenceName: String,
    val referenceType: String,
    val declaredClassName: String? = null,
    val sourceFile: String? = null,
    val lineNumber: Int? = null
)
