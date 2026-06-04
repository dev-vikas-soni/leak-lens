package com.github.devvikassoni.leaklens.reporting

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReportExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDummyLeaks(): List<LeakInfo> {
        return listOf(
            LeakInfo(
                signature = "sig_1",
                shortDescription = "Test Leak <with> HTML characters & generic types",
                retainedObjectClassName = "com.example.GenericClass<Type>",
                retainedByteSize = 2048,
                retainedObjectCount = 1,
                leakTrace = "Trace with \"quotes\" and <brackets>",
                severity = LeakSeverity.CRITICAL,
                isLibraryLeak = false,
                referenceChain = emptyList(),
                suggestedFix = "Fix it by removing <Type>"
            )
        )
    }

    @Test
    fun testExportHtmlEscaping() {
        val leaks = createDummyLeaks()
        val file = tempFolder.newFile("report.html")
        ReportExporter.exportHtml(leaks, file)

        val content = file.readText()
        assertTrue("HTML should escape <", content.contains("&lt;with&gt;"))
        assertTrue("HTML should escape > in class name", content.contains("com.example.GenericClass&lt;Type&gt;"))
        assertTrue("HTML should escape quotes", content.contains("&quot;quotes&quot;"))
        assertTrue("HTML should include suggested fix", content.contains("Fix it by removing &lt;Type&gt;"))
    }

    @Test
    fun testExportJsonEscaping() {
        val leaks = createDummyLeaks()
        val file = tempFolder.newFile("report.json")
        ReportExporter.exportJson(leaks, file)

        val content = file.readText()
        assertTrue("JSON should escape quotes", content.contains("\\\"quotes\\\""))
        assertTrue("JSON should have class name", content.contains("com.example.GenericClass<Type>"))
    }

    @Test
    fun testExportSarifOutput() {
        val leaks = createDummyLeaks()
        val file = tempFolder.newFile("report.sarif")
        ReportExporter.exportSarif(leaks, file)

        val content = file.readText()
        assertTrue("SARIF should contain schema", content.contains("\"\$schema\""))
        assertTrue("SARIF should contain ruleId for critical", content.contains("\"ruleId\": \"leak/critical\""))
        assertTrue("SARIF should contain escaped message", content.contains("Test Leak <with> HTML characters & generic types"))
        assertTrue("SARIF should have proper artifact location", content.contains("\"uri\": \"com/example/GenericClass<Type>.java\""))
    }
}
