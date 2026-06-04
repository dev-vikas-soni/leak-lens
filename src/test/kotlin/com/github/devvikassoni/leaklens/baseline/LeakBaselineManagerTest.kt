package com.github.devvikassoni.leaklens.baseline

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File

class LeakBaselineManagerTest : BasePlatformTestCase() {

    private lateinit var baselineManager: LeakBaselineManager
    private lateinit var baselineFile: File

    override fun setUp() {
        super.setUp()
        // Delete existing baseline file for isolation
        baselineFile = File(project.basePath ?: System.getProperty("java.io.tmpdir"), "leak-baseline.json")
        if (baselineFile.exists()) {
            baselineFile.delete()
        }
        baselineManager = LeakBaselineManager(project)
    }

    override fun tearDown() {
        if (baselineFile.exists()) {
            baselineFile.delete()
        }
        super.tearDown()
    }

    @Test
    fun testAddToBaselineAndCheck() {
        val leak = LeakInfo(
            signature = "baseline_sig_123",
            shortDescription = "Test Leak in Baseline",
            retainedObjectClassName = "com.test.LeakedClass",
            retainedByteSize = 100,
            retainedObjectCount = 1,
            leakTrace = "trace...",
            severity = LeakSeverity.WARNING,
            isLibraryLeak = false,
            referenceChain = emptyList(),
            suggestedFix = null
        )

        // Initially not in baseline
        assertFalse(baselineManager.isInBaseline(leak))

        // Add to baseline
        baselineManager.addToBaseline(leak)

        // Should be in baseline now
        assertTrue(baselineManager.isInBaseline(leak))
        assertEquals(1, baselineManager.getBaselineCount())

        // File should be created and contain signature
        assertTrue(baselineFile.exists())
        val content = baselineFile.readText()
        assertTrue(content.contains("baseline_sig_123"))
        assertTrue(content.contains("com.test.LeakedClass"))
    }

    @Test
    fun testFilterNewLeaks() {
        val leak1 = LeakInfo(
            signature = "sig_1",
            shortDescription = "Leak 1",
            retainedObjectClassName = "com.test.Class1",
            retainedByteSize = 100,
            retainedObjectCount = 1,
            leakTrace = "trace...",
            severity = LeakSeverity.WARNING,
            isLibraryLeak = false,
            referenceChain = emptyList(),
            suggestedFix = null
        )
        val leak2 = LeakInfo(
            signature = "sig_2",
            shortDescription = "Leak 2",
            retainedObjectClassName = "com.test.Class2",
            retainedByteSize = 100,
            retainedObjectCount = 1,
            leakTrace = "trace...",
            severity = LeakSeverity.WARNING,
            isLibraryLeak = false,
            referenceChain = emptyList(),
            suggestedFix = null
        )

        baselineManager.addToBaseline(leak1)

        val newLeaks = baselineManager.filterNewLeaks(listOf(leak1, leak2))
        assertEquals(1, newLeaks.size)
        assertEquals("sig_2", newLeaks[0].signature)
    }

    @Test
    fun testLoadBaseline() {
        val leak = LeakInfo(
            signature = "loaded_sig_999",
            shortDescription = "Loaded Leak",
            retainedObjectClassName = "com.test.Loaded",
            retainedByteSize = 100,
            retainedObjectCount = 1,
            leakTrace = "trace...",
            severity = LeakSeverity.WARNING,
            isLibraryLeak = false,
            referenceChain = emptyList(),
            suggestedFix = null
        )
        baselineManager.saveBaseline(listOf(leak))

        // Create new manager instance to test loading from disk
        val newManager = LeakBaselineManager(project)
        assertTrue(newManager.isInBaseline(leak))
    }
}
