package com.github.devvikassoni.leaklens.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class LeakInspectionTestBase : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        AndroidStubs.install(myFixture)
    }

    /** Text ranges highlighted by LeakLens in [text], sorted. */
    protected fun flagged(tool: LocalInspectionTool, fileName: String, text: String): List<String> {
        myFixture.enableInspections(tool)
        myFixture.configureByText(fileName, text.trimIndent())
        return myFixture.doHighlighting()
            .filter { it.description?.startsWith("LeakLens:") == true }
            .map { it.text }
            .sorted()
    }

    protected fun assertFlagged(tool: LocalInspectionTool, fileName: String, text: String, vararg expected: String) {
        assertEquals(expected.sorted(), flagged(tool, fileName, text))
    }

    /** Applies the quick fix whose name starts with [fixPrefix] and returns the resulting file text. */
    protected fun applyFix(tool: LocalInspectionTool, fileName: String, text: String, fixPrefix: String): String {
        flagged(tool, fileName, text)
        val fixes = myFixture.getAllQuickFixes()
        val fix = fixes.firstOrNull { it.text.startsWith(fixPrefix) }
            ?: error("No fix '$fixPrefix' in ${fixes.map { it.text }}")
        myFixture.launchAction(fix)
        return myFixture.editor.document.text
    }

    protected fun assertContainsAll(actual: String, vararg fragments: String) {
        val normalized = actual.replace(Regex("\\s+"), " ")
        for (fragment in fragments) {
            val expected = fragment.replace(Regex("\\s+"), " ")
            assertTrue("Expected '$fragment' in:\n$actual", normalized.contains(expected))
        }
    }
}
