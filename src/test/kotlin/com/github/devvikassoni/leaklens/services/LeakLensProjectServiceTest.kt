package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class LeakLensProjectServiceTest : BasePlatformTestCase() {

    private lateinit var service: LeakLensProjectService

    override fun setUp() {
        super.setUp()
        service = LeakLensProjectService.getInstance(project)
    }

    fun testUpdateLeaks() = runBlocking {
        val leak = LeakInfo(
            signature = "sig",
            shortDescription = "desc",
            leakTrace = "trace",
            retainedObjectClassName = "com.example.MainActivity",
            retainedByteSize = 1024L,
            retainedObjectCount = 1,
            severity = LeakSeverity.CRITICAL,
            referenceChain = emptyList()
        )

        service.updateLeaks(listOf(leak))
        
        assertEquals(1, service.leaks.value.size)
        assertEquals("com.example.MainActivity", service.leaks.value[0].retainedObjectClassName)
    }

    fun testSetAnalyzing() {
        service.setAnalyzing(true)
        assertTrue(service.isAnalyzing.value)
        
        service.setAnalyzing(false)
        assertFalse(service.isAnalyzing.value)
    }

    fun testClearLeaks() {
        val leak = LeakInfo(
            signature = "sig",
            shortDescription = "desc",
            leakTrace = "trace",
            retainedObjectClassName = "com.example.MainActivity",
            retainedByteSize = 1024L,
            retainedObjectCount = 1,
            severity = LeakSeverity.CRITICAL,
            referenceChain = emptyList()
        )
        service.updateLeaks(listOf(leak))
        assertEquals(1, service.leaks.value.size)

        service.clearLeaks()
        assertTrue(service.leaks.value.isEmpty())
    }
}
