package com.github.devvikassoni.leaklens.ai

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * AI-assisted leak analysis service.
 * - Default: DISABLED (no network calls)
 * - When enabled: sends anonymized leak traces to AI API for contextual fix generation
 * - Supports OpenAI and Google Gemini
 * - Clearly marks AI-generated suggestions with a badge
 * - Respects privacy: strips package names by default
 */
@Service(Service.Level.PROJECT)
class AiAnalysisService(private val project: Project) {

    private val logger = thisLogger()

    companion object {
        const val AI_BADGE = "🤖 AI-Generated Suggestion"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"

        fun getInstance(project: Project): AiAnalysisService =
            project.getService(AiAnalysisService::class.java)
    }

    /**
     * Check if AI analysis is enabled and properly configured.
     */
    fun isEnabled(): Boolean {
        val settings = LeakLensSettingsState.getInstance(project)
        return settings.aiEnabled && settings.aiProvider != "none" && settings.aiApiKey.isNotBlank()
    }

    /**
     * Generate an AI-assisted fix suggestion for a leak that wasn't matched by static rules.
     * Returns null if AI is disabled or the request fails.
     */
    fun generateFixSuggestion(leak: LeakInfo): String? {
        if (!isEnabled()) return null

        val settings = LeakLensSettingsState.getInstance(project)

        try {
            val prompt = buildPrompt(leak, settings.aiAnonymizePackageNames)

            val response = when (settings.aiProvider) {
                "openai" -> callOpenAI(prompt, settings.aiApiKey)
                "gemini" -> callGemini(prompt, settings.aiApiKey)
                else -> null
            }

            return if (response != null) {
                "$AI_BADGE\n\n$response"
            } else null
        } catch (e: Exception) {
            logger.warn("LeakLens AI: Failed to generate suggestion", e)
            return null
        }
    }

    /**
     * Enrich leaks that don't have static fix suggestions with AI-generated ones.
     */
    fun enrichWithAiSuggestions(leaks: List<LeakInfo>): List<LeakInfo> {
        if (!isEnabled()) return leaks

        return leaks.map { leak ->
            if (leak.suggestedFix == null || leak.suggestedFix.contains("No fix suggestion available")) {
                val aiSuggestion = generateFixSuggestion(leak)
                if (aiSuggestion != null) {
                    leak.copy(suggestedFix = aiSuggestion)
                } else leak
            } else leak
        }
    }

    private fun buildPrompt(leak: LeakInfo, anonymize: Boolean): String {
        val trace = if (anonymize) anonymizeTrace(leak.leakTrace) else leak.leakTrace
        val className = if (anonymize) anonymizeClassName(leak.retainedObjectClassName) else leak.retainedObjectClassName

        return """
            You are an expert Android developer specializing in memory leak detection and fixing.
            
            Analyze this Android memory leak and provide:
            1. Root cause explanation (2-3 sentences)
            2. Specific fix with code snippet
            3. Prevention tip
            
            Leak Details:
            - Leaking class: $className
            - Severity: ${leak.severity.displayName}
            - Retained size: ${leak.retainedByteSize / 1024} KB
            - Is library leak: ${leak.isLibraryLeak}
            
            Reference chain:
            ${leak.referenceChain.joinToString("\n") { ref ->
                val ownerName = if (anonymize) anonymizeClassName(ref.owningClassName) else ref.owningClassName
                "  → $ownerName.${ref.referenceName} (${ref.referenceType})"
            }}
            
            Full trace:
            $trace
            
            Provide a concise, actionable fix. Include Kotlin code snippets.
        """.trimIndent()
    }

    private fun anonymizeClassName(className: String): String {
        // Replace package prefixes with generic ones, keep class name
        val parts = className.split(".")
        if (parts.size <= 2) return className
        val simpleName = parts.last()
        return "app.package.$simpleName"
    }

    private fun anonymizeTrace(trace: String): String {
        // Replace common package patterns with generic prefixes
        return trace
            .replace(Regex("""com\.\w+\.\w+"""), "app.pkg")
            .replace(Regex("""org\.\w+\.\w+"""), "lib.pkg")
    }

    private fun callOpenAI(prompt: String, apiKey: String): String? {
        val url = URI(OPENAI_URL).toURL()
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val requestBody = """
                {
                    "model": "gpt-4o-mini",
                    "messages": [
                        {"role": "system", "content": "You are an Android memory leak expert. Provide concise, actionable fixes."},
                        {"role": "user", "content": ${escapeJson(prompt)}}
                    ],
                    "max_tokens": 500,
                    "temperature": 0.3
                }
            """.trimIndent()

            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                return extractOpenAIContent(response)
            } else {
                logger.warn("LeakLens AI: OpenAI returned ${connection.responseCode}")
                return null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun callGemini(prompt: String, apiKey: String): String? {
        val url = URI("$GEMINI_URL?key=$apiKey").toURL()
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val requestBody = """
                {
                    "contents": [{
                        "parts": [{"text": ${escapeJson(prompt)}}]
                    }],
                    "generationConfig": {
                        "maxOutputTokens": 500,
                        "temperature": 0.3
                    }
                }
            """.trimIndent()

            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                return extractGeminiContent(response)
            } else {
                logger.warn("LeakLens AI: Gemini returned ${connection.responseCode}")
                return null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractOpenAIContent(json: String): String? {
        // Simple JSON extraction without adding a JSON library dependency
        val contentStart = json.indexOf("\"content\":\"")
        if (contentStart == -1) {
            val contentStart2 = json.indexOf("\"content\": \"")
            if (contentStart2 == -1) return null
            val start = contentStart2 + 12
            val end = json.indexOf("\"", start)
            return if (end > start) json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"") else null
        }
        val start = contentStart + 11
        val end = findClosingQuote(json, start)
        return if (end > start) json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"") else null
    }

    private fun extractGeminiContent(json: String): String? {
        val textStart = json.indexOf("\"text\":\"")
        if (textStart == -1) {
            val textStart2 = json.indexOf("\"text\": \"")
            if (textStart2 == -1) return null
            val start = textStart2 + 9
            val end = findClosingQuote(json, start)
            return if (end > start) json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"") else null
        }
        val start = textStart + 8
        val end = findClosingQuote(json, start)
        return if (end > start) json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"") else null
    }

    private fun findClosingQuote(json: String, startAfterQuote: Int): Int {
        var i = startAfterQuote
        while (i < json.length) {
            if (json[i] == '"' && json[i - 1] != '\\') return i
            i++
        }
        return -1
    }

    private fun escapeJson(text: String): String {
        return "\"" + text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}

