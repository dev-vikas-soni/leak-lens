package com.github.devvikassoni.leaklens.reporting

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Phase 7: Export analysis results as HTML, JSON, or SARIF reports.
 */
object ReportExporter {

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#x27;")

    fun exportHtml(leaks: List<LeakInfo>, outputFile: File) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val html = buildString {
            appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'>")
            appendLine("<title>LeakLens Report - $dateStr</title>")
            appendLine("<style>body{font-family:sans-serif;margin:20px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ddd;padding:8px;text-align:left}th{background:#f4f4f4}.critical{color:#d32f2f}.warning{color:#f57c00}.library{color:#388e3c}pre{background:#f5f5f5;padding:10px;overflow-x:auto}</style>")
            appendLine("</head><body>")
            appendLine("<h1>🔍 LeakLens Memory Leak Report</h1>")
            appendLine("<p>Generated: $dateStr | Total leaks: ${leaks.size}</p>")
            appendLine("<table><tr><th>Severity</th><th>Class</th><th>Description</th><th>Retained</th></tr>")
            for (leak in leaks) {
                val cls = when (leak.severity) {
                    LeakSeverity.CRITICAL -> "critical"
                    LeakSeverity.WARNING -> "warning"
                    LeakSeverity.LIBRARY_LEAK -> "library"
                }
                appendLine("<tr class='$cls'><td>${leak.severity.displayName}</td><td>${escapeHtml(leak.retainedObjectClassName)}</td><td>${escapeHtml(leak.shortDescription)}</td><td>${leak.retainedByteSize / 1024} KB</td></tr>")
            }
            appendLine("</table>")
            for (leak in leaks) {
                appendLine("<h3>${escapeHtml(leak.retainedObjectClassName)}</h3>")
                appendLine("<pre>${escapeHtml(leak.leakTrace)}</pre>")
                if (leak.suggestedFix != null) {
                    appendLine("<h4>Suggested Fix:</h4><pre>${escapeHtml(leak.suggestedFix)}</pre>")
                }
            }
            appendLine("</body></html>")
        }
        outputFile.writeText(html)
    }

    fun exportJson(leaks: List<LeakInfo>, outputFile: File) {
        val json = buildString {
            appendLine("{")
            appendLine("  \"generated\": \"${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date())}\",")
            appendLine("  \"totalLeaks\": ${leaks.size},")
            appendLine("  \"leaks\": [")
            leaks.forEachIndexed { i, leak ->
                appendLine("    {")
                appendLine("      \"signature\": \"${escapeJson(leak.signature)}\",")
                appendLine("      \"severity\": \"${leak.severity.name}\",")
                appendLine("      \"className\": \"${escapeJson(leak.retainedObjectClassName)}\",")
                appendLine("      \"description\": \"${escapeJson(leak.shortDescription)}\",")
                appendLine("      \"retainedBytes\": ${leak.retainedByteSize},")
                appendLine("      \"isLibraryLeak\": ${leak.isLibraryLeak},")
                appendLine("      \"trace\": \"${escapeJson(leak.leakTrace)}\",")
                appendLine("      \"suggestedFix\": ${if (leak.suggestedFix != null) "\"${escapeJson(leak.suggestedFix)}\"" else "null"}")
                append("    }")
                if (i < leaks.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
        outputFile.writeText(json)
    }

    /**
     * Export as SARIF (Static Analysis Results Interchange Format).
     * Compatible with GitHub Code Scanning and SonarQube.
     */
    fun exportSarif(leaks: List<LeakInfo>, outputFile: File) {
        val sarif = buildString {
            appendLine("""{
  "${"$"}schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/main/sarif-2.1/schema/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [{
    "tool": {
      "driver": {
        "name": "LeakLens",
        "version": "0.6.0",
        "informationUri": "https://github.com/dev-vikas-soni/leak-lens",
        "rules": [
          {"id": "leak/critical", "shortDescription": {"text": "Critical memory leak (Activity/Fragment)"}, "defaultConfiguration": {"level": "error"}},
          {"id": "leak/warning", "shortDescription": {"text": "Memory leak warning"}, "defaultConfiguration": {"level": "warning"}},
          {"id": "leak/library", "shortDescription": {"text": "Known library/framework leak"}, "defaultConfiguration": {"level": "note"}}
        ]
      }
    },
    "results": [""")
            leaks.forEachIndexed { i, leak ->
                val ruleId = when (leak.severity) {
                    LeakSeverity.CRITICAL -> "leak/critical"
                    LeakSeverity.WARNING -> "leak/warning"
                    LeakSeverity.LIBRARY_LEAK -> "leak/library"
                }
                val level = when (leak.severity) {
                    LeakSeverity.CRITICAL -> "error"
                    LeakSeverity.WARNING -> "warning"
                    LeakSeverity.LIBRARY_LEAK -> "note"
                }
                val className = leak.retainedObjectClassName.replace('.', '/')
                append("""      {
        "ruleId": "$ruleId",
        "level": "$level",
        "message": {"text": "${escapeJson(leak.shortDescription)}"},
        "locations": [{"physicalLocation": {"artifactLocation": {"uri": "$className.java"}}}]
      }""")
                if (i < leaks.size - 1) appendLine(",") else appendLine()
            }
            appendLine("""    ]
  }]
}""")
        }
        outputFile.writeText(sarif)
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}

