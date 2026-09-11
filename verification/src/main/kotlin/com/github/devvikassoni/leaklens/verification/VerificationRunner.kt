package com.github.devvikassoni.leaklens.verification

import com.github.devvikassoni.leaklens.shark.LeakLensObjectInspectors
import com.google.gson.GsonBuilder
import java.io.File
import kotlin.system.exitProcess
import shark.AndroidObjectInspectors
import shark.AndroidReferenceMatchers
import shark.FileSourceProvider
import shark.FilteringLeakingObjectFinder
import shark.HeapAnalysisSuccess
import shark.HeapAnalyzer
import shark.HprofHeapGraph.Companion.openHeapGraph
import shark.MetadataExtractor
import shark.OnAnalysisProgressListener

fun main(args: Array<String>) {
    val scenarioArg = args.indexOf("--scenario")
    val targetScenario = if (scenarioArg != -1) args[scenarioArg + 1] else null

    val runner = VerificationRunner()
    val success = runner.runVerification(targetScenario)

    if (!success) {
        exitProcess(1)
    }
}

class VerificationRunner {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val baseDir = if (File("verification/golden").exists()) {
        File("verification")
    } else {
        File(".")
    }

    private val goldenDir = File(baseDir, "golden")
    private val buildDir = File(baseDir, "build")
    private val actualDir = File(buildDir, "actual")

    fun runVerification(targetScenario: String?): Boolean {
        println("Starting LeakLens Verification Engine...")
        actualDir.mkdirs()

        val scenarios = goldenDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        var allPassed = true

        for (scenarioFolder in scenarios) {
            val scenarioId = scenarioFolder.name
            if (targetScenario != null && scenarioId != targetScenario) continue

            println("\nScenario: $scenarioId")

            val hprofFile = File(scenarioFolder, "input.hprof")
            if (!hprofFile.exists()) {
                println("  [SKIP] input.hprof missing")
                continue
            }

            val expectedFile = File(scenarioFolder, "expected.json")
            if (!expectedFile.exists()) {
                println("  [SKIP] expected.json missing")
                continue
            }

            val expected = gson.fromJson(expectedFile.readText(), NormalizedLeak::class.java)

            // 1. Analyze
            val actualLeaks = analyzeHprofForVerification(hprofFile)

            // 2. Find best match
            val actualLeak = actualLeaks.find { it.className == expected.className }
                ?: actualLeaks.firstOrNull()

            if (actualLeak == null) {
                println("  [FAIL] No leaks found in heap.")
                allPassed = false
                continue
            }

            // 3. Persist Actual
            val actualFile = File(actualDir, "$scenarioId.actual.json")
            actualFile.writeText(gson.toJson(actualLeak))

            // 4. Compare
            val result =
                GoldenComparator.compare(actualLeak, expected).copy(scenarioId = scenarioId)

            if (result.isMatch) {
                println("  [PASS] Output matches golden.")
            } else {
                println("  [FAIL] Mismatch detected.")
                result.differences.forEach { println("    $it") }
                allPassed = false
            }
        }

        return allPassed
    }

    private fun analyzeHprofForVerification(file: File): List<NormalizedLeak> {
        val analyzer = HeapAnalyzer(OnAnalysisProgressListener.NO_OP)
        val sourceProvider = FileSourceProvider(file)

        return sourceProvider.openHeapGraph().use { graph ->
            val analysis = analyzer.analyze(
                heapDumpFile = file,
                graph = graph,
                leakingObjectFinder = FilteringLeakingObjectFinder(
                    AndroidObjectInspectors.appLeakingObjectFilters
                ),
                referenceMatchers = AndroidReferenceMatchers.appDefaults,
                objectInspectors = LeakLensObjectInspectors.canonicalConfig,
                computeRetainedHeapSize = true,
                metadataExtractor = MetadataExtractor.NO_OP
            )

            if (analysis is HeapAnalysisSuccess) {
                LeakNormalizer.normalize(analysis)
            } else {
                emptyList()
            }
        }
    }
}
