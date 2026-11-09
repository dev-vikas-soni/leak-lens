package com.github.devvikassoni.leaklens.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class SharkAnalysisServiceTest : BasePlatformTestCase() {

    private lateinit var service: SharkAnalysisService

    override fun setUp() {
        super.setUp()
        service = SharkAnalysisService.getInstance(project)
    }

    fun testServiceIsRegistered() {
        assertNotNull(service)
    }

    fun testAnalyzeNonExistentFile() {
        val nonExistentFile = File("non_existent.hprof")
        val leaks = service.analyzeHprof(nonExistentFile)
        assertTrue(leaks.isEmpty())
    }

    fun testAnalyzeCorruptFileThrowsInsteadOfReportingNoLeaks() {
        val corrupt = File.createTempFile("leaklens_corrupt_", ".hprof")
        try {
            corrupt.writeText("this is not an hprof file")
            assertThrows(HeapAnalysisFailedException::class.java) {
                service.analyzeHprof(corrupt)
            }
        } finally {
            corrupt.delete()
        }
    }
}
