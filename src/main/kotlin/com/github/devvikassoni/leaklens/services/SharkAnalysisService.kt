package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.github.devvikassoni.leaklens.shark.LeakLensObjectInspectors
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import shark.*
import java.io.File

@Service(Service.Level.PROJECT)
class SharkAnalysisService(private val project: Project) {

    private val logger = thisLogger()

    fun analyzeHprof(hprofFile: File): List<LeakInfo> {
        logger.info("LeakLens: Starting Shark analysis of ${hprofFile.name}")

        val heapAnalyzer = HeapAnalyzer(OnAnalysisProgressListener { step ->
            logger.info("LeakLens: Analysis step - $step")
        })

        // Combine Android defaults with LeakLens custom inspectors
        val objectInspectors = AndroidObjectInspectors.appDefaults + LeakLensObjectInspectors.all

        val analysis = heapAnalyzer.analyze(
            heapDumpFile = hprofFile,
            leakingObjectFinder = FilteringLeakingObjectFinder(
                AndroidObjectInspectors.appLeakingObjectFilters
            ),
            referenceMatchers = AndroidReferenceMatchers.appDefaults,
            objectInspectors = objectInspectors,
            computeRetainedHeapSize = true
        )

        return when (analysis) {
            is HeapAnalysisSuccess -> {
                logger.info("LeakLens: Analysis successful - ${analysis.allLeaks.sumOf { it.leakTraces.size }} leak traces found")
                convertToLeakInfoList(analysis)
            }
            is HeapAnalysisFailure -> {
                logger.error("LeakLens: Analysis failed", analysis.exception)
                emptyList()
            }
        }
    }

    private fun convertToLeakInfoList(analysis: HeapAnalysisSuccess): List<LeakInfo> {
        val leaks = mutableListOf<LeakInfo>()

        for (applicationLeak in analysis.applicationLeaks) {
            for (leakTrace in applicationLeak.leakTraces) {
                leaks.add(convertLeakTrace(leakTrace, applicationLeak.shortDescription, isLibrary = false))
            }
        }

        for (libraryLeak in analysis.libraryLeaks) {
            for (leakTrace in libraryLeak.leakTraces) {
                leaks.add(convertLeakTrace(leakTrace, libraryLeak.shortDescription, isLibrary = true))
            }
        }

        return leaks
    }

    private fun convertLeakTrace(
        leakTrace: LeakTrace,
        shortDescription: String,
        isLibrary: Boolean
    ): LeakInfo {
        val severity = when {
            isLibrary -> LeakSeverity.LIBRARY_LEAK
            leakTrace.leakingObject.className.contains("Activity") ||
            leakTrace.leakingObject.className.contains("Fragment") -> LeakSeverity.CRITICAL
            else -> LeakSeverity.WARNING
        }

        val references = leakTrace.referencePath.map { ref ->
            LeakTraceReference(
                owningClassName = ref.owningClassName,
                referenceName = ref.referenceName,
                referenceType = ref.referenceType.name,
                declaredClassName = ref.originObject.className
            )
        }

        return LeakInfo(
            signature = leakTrace.signature,
            shortDescription = shortDescription,
            leakTrace = leakTrace.toString(),
            retainedObjectClassName = leakTrace.leakingObject.className,
            retainedByteSize = leakTrace.retainedHeapByteSize?.toLong() ?: 0L,
            retainedObjectCount = leakTrace.retainedObjectCount ?: 0,
            severity = severity,
            referenceChain = references,
            isLibraryLeak = isLibrary
        )
    }

    companion object {
        fun getInstance(project: Project): SharkAnalysisService =
            project.getService(SharkAnalysisService::class.java)
    }
}

