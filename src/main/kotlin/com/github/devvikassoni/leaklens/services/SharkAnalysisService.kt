package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.compat.CompatibilityLogger
import com.github.devvikassoni.leaklens.compat.ProgressFacade
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.github.devvikassoni.leaklens.shark.LeakLensObjectInspectors
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import shark.*
import shark.HprofHeapGraph.Companion.openHeapGraph
import java.io.File
import java.util.concurrent.CancellationException

/**
 * Thrown when Shark could not analyze a heap dump (e.g. truncated or corrupt HPROF),
 * so callers can distinguish a failure from a clean heap with no leaks.
 */
class HeapAnalysisFailedException(cause: Throwable) :
    RuntimeException("Heap analysis failed: ${cause.message ?: cause.javaClass.simpleName}", cause)

/**
 * Service that encapsulates the Shark heap analysis engine.
 *
 * It is configured with Android-specific inspectors and optimized for large
 * heap dump handling on the developer's machine.
 */
@Service(Service.Level.PROJECT)
class SharkAnalysisService {

    companion object {
        private const val LARGE_HEAP_THRESHOLD_BYTES = 500 * 1024 * 1024L // 500 MB

        fun getInstance(project: Project): SharkAnalysisService =
            project.getService(SharkAnalysisService::class.java)
    }

    /**
     * Analyze a heap dump with optimizations for large files and cancellation support.
     *
     * @throws HeapAnalysisFailedException if Shark fails to analyze the dump.
     * @throws ProcessCanceledException if the user cancels via [indicator].
     */
    fun analyzeHprof(
        hprofFile: File,
        indicator: com.intellij.openapi.progress.ProgressIndicator? = null
    ): List<LeakInfo> {
        if (!hprofFile.exists()) {
            CompatibilityLogger.warn("LeakLens: Cannot analyze non-existent file: ${hprofFile.absolutePath}")
            return emptyList()
        }

        val fileSize = hprofFile.length()
        val isLargeFile = fileSize > LARGE_HEAP_THRESHOLD_BYTES

        CompatibilityLogger.info(
            "LeakLens: Starting Shark analysis of ${hprofFile.name} (${fileSize / 1024 / 1024} MB)"
        )

        if (isLargeFile) {
            CompatibilityLogger.warn(
                "LeakLens: ${hprofFile.name} is large ($fileSize bytes). Analysis may be slow and memory-intensive."
            )
        }

        val heapAnalyzer = HeapAnalyzer { step ->
            ProgressFacade.checkCanceled(indicator)
            CompatibilityLogger.info("Analysis step: $step")
            ProgressFacade.setText(indicator, "Analyzing heap: $step")
        }

        // Combine Android defaults with LeakLens custom inspectors
        val objectInspectors = AndroidObjectInspectors.appDefaults + LeakLensObjectInspectors.all

        val sourceProvider = FileSourceProvider(hprofFile)
        val analysis = try {
            sourceProvider.openHeapGraph().use { graph ->
                heapAnalyzer.analyze(
                    heapDumpFile = hprofFile,
                    graph = graph,
                    leakingObjectFinder = FilteringLeakingObjectFinder(
                        AndroidObjectInspectors.appLeakingObjectFilters
                    ),
                    referenceMatchers = AndroidReferenceMatchers.appDefaults,
                    objectInspectors = objectInspectors,
                    computeRetainedHeapSize = true,
                    metadataExtractor = MetadataExtractor.NO_OP
                )
            }
        } catch (e: Exception) {
            findCancellation(e)?.let { throw it }
            CompatibilityLogger.warn("LeakLens: Could not open heap dump ${hprofFile.name}", e)
            throw HeapAnalysisFailedException(e)
        }

        return when (analysis) {
            is HeapAnalysisSuccess -> {
                val leakCount = analysis.allLeaks.sumOf { it.leakTraces.size }
                CompatibilityLogger.info("LeakLens: Analysis successful - $leakCount leak traces found")

                if (leakCount == 0) {
                    CompatibilityLogger.info("LeakLens: No leaks found in success object. Check matching filters.")
                }

                convertToLeakInfoList(analysis)
            }

            is HeapAnalysisFailure -> {
                // Shark catches every Throwable, including our cancellation from the progress listener.
                findCancellation(analysis.exception)?.let { throw it }
                CompatibilityLogger.warn("LeakLens: Analysis failed with Shark exception", analysis.exception)
                throw HeapAnalysisFailedException(analysis.exception.cause ?: analysis.exception)
            }
        }
    }

    private fun findCancellation(throwable: Throwable): RuntimeException? {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is ProcessCanceledException || current is CancellationException) {
                return current as RuntimeException
            }
            current = current.cause.takeIf { it !== current }
        }
        return null
    }

    private fun convertToLeakInfoList(analysis: HeapAnalysisSuccess): List<LeakInfo> {
        val leaks = mutableListOf<LeakInfo>()

        for (applicationLeak in analysis.applicationLeaks) {
            for (leakTrace in applicationLeak.leakTraces) {
                leaks.add(
                    convertLeakTrace(
                        leakTrace,
                        applicationLeak.shortDescription,
                        isLibrary = false
                    )
                )
            }
        }

        for (libraryLeak in analysis.libraryLeaks) {
            for (leakTrace in libraryLeak.leakTraces) {
                leaks.add(
                    convertLeakTrace(
                        leakTrace,
                        libraryLeak.shortDescription,
                        isLibrary = true
                    )
                )
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
}
