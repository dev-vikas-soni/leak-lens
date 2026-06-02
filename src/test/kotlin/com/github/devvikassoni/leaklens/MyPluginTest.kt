package com.github.devvikassoni.leaklens

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.devvikassoni.leaklens.services.LeakLensProjectService

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testProjectServiceInitialization() {
        val projectService = LeakLensProjectService.getInstance(project)
        assertNotNull(projectService)
    }

    fun testEmptyLeaksOnStart() {
        val projectService = LeakLensProjectService.getInstance(project)
        assertTrue(projectService.leaks.value.isEmpty())
    }
}
