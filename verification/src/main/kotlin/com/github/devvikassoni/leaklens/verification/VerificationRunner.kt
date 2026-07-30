package com.github.devvikassoni.leaklens.verification

import com.google.gson.GsonBuilder
import java.io.File
import shark.HeapAnalyzer
import shark.OnAnalysisProgressListener

fun main(args: Array<String>) {
    val scenarioArg = args.indexOf("--scenario")
    val targetScenario = if (scenarioArg != -1) args[scenarioArg + 1] else null

    val runner = VerificationRunner()
    val success = runner.runVerification(targetScenario)

    if (!success) {
        System.exit(1)
    }
}

class VerificationRunner {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val goldenDir = File("verification/golden")
    private val buildDir = File("verification/build")
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
                println("  [SKIP] No input.hprof found at ${hprofFile.path}")
                continue
            }

            val expectedFile = File(scenarioFolder, "expected.json")
            if (!expectedFile.exists()) {
                println("  [SKIP] No expected.json found at ${expectedFile.path}")
                continue
            }

            // 1. Analyze and Normalize
            val actualLeak = analyzeHprof(hprofFile)

            if (actualLeak == null) {
                println("  [FAIL] Analysis failed to produce any application leaks.")
                allPassed = false
                continue
            }

            // 2. Persist Actual for Diffing
            val actualFile = File(actualDir, "$scenarioId.actual.json")
            actualFile.writeText(gson.toJson(actualLeak))

            // 3. Semantic Comparison
            val expected = gson.fromJson(expectedFile.readText(), NormalizedLeak::class.java)
            val result =
                GoldenComparator.compare(actualLeak, expected).copy(scenarioId = scenarioId)

            if (result.isMatch) {
                println("  [PASS] Output matches golden.")
            } else {
                println("  [FAIL] Mismatch detected between expected.json and actual.json")
                result.differences.forEach { println("    $it") }
                println("  Check generated actual: ${actualFile.absolutePath}")
                allPassed = false
            }
        }

        return allPassed
    }

    private fun analyzeHprof(file: File): NormalizedLeak? {
        val analyzer = HeapAnalyzer(OnAnalysisProgressListener.NO_OP)
        val analysis = analyzer.analyze(
            heapDumpFile = file,
            leakingObjectFinder = shark.FilteringLeakingObjectFinder(
                shark.AndroidObjectInspectors.appLeakingObjectFilters
            ),
            referenceMatchers = shark.AndroidReferenceMatchers.appDefaults,
            computeRetainedHeapSize = true,
            objectInspectors = shark.AndroidObjectInspectors.appDefaults
        )

        return if (analysis is shark.HeapAnalysisSuccess) {
            LeakNormalizer.normalize(analysis).firstOrNull()
        } else {
            null
        }
    }
}
