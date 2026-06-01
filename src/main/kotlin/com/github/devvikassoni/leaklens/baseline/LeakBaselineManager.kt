package com.github.devvikassoni.leaklens.baseline

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Phase 7: Leak baseline management.
 * Stores suppressed/accepted leak signatures in VCS-tracked file (like lint-baseline.xml).
 * New leaks not in baseline trigger warnings; baseline leaks are silently ignored.
 */
@Service(Service.Level.PROJECT)
class LeakBaselineManager(private val project: Project) {

    private val logger = thisLogger()
    private val baselineSignatures = mutableSetOf<String>()

    private val baselineFile: File
        get() = File(project.basePath ?: ".", "leak-baseline.json")

    init {
        loadBaseline()
    }

    fun loadBaseline() {
        try {
            if (baselineFile.exists()) {
                val content = baselineFile.readText()
                val signatures = Regex("\"signature\":\\s*\"([^\"]+)\"").findAll(content)
                    .map { it.groupValues[1] }.toSet()
                baselineSignatures.clear()
                baselineSignatures.addAll(signatures)
                logger.info("LeakLens: Loaded ${baselineSignatures.size} baseline entries from ${baselineFile.name}")
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to load baseline", e)
        }
    }

    fun saveBaseline(leaks: List<LeakInfo>) {
        val json = buildString {
            appendLine("{")
            appendLine("  \"description\": \"LeakLens baseline - suppressed leak signatures\",")
            appendLine("  \"generatedAt\": \"${java.time.Instant.now()}\",")
            appendLine("  \"leaks\": [")
            leaks.forEachIndexed { i, leak ->
                append("    {\"signature\": \"${leak.signature}\", \"class\": \"${leak.retainedObjectClassName}\", \"description\": \"${leak.shortDescription.replace("\"", "\\\"")}\"}")
                if (i < leaks.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
        baselineFile.writeText(json)
        baselineSignatures.clear()
        baselineSignatures.addAll(leaks.map { it.signature })
        logger.info("LeakLens: Saved ${leaks.size} entries to baseline")
    }

    fun addToBaseline(leak: LeakInfo) {
        baselineSignatures.add(leak.signature)
        // Re-save full baseline
        val existingLeaks = if (baselineFile.exists()) {
            // Just append the new signature
            val content = baselineFile.readText()
            baselineFile.writeText(content) // keep existing, we'll do full save on next analysis
        } else null
        logger.info("LeakLens: Added ${leak.signature} to baseline")
    }

    fun isInBaseline(leak: LeakInfo): Boolean = leak.signature in baselineSignatures

    /**
     * Filter out baseline leaks, returning only new/unaccepted leaks.
     */
    fun filterNewLeaks(leaks: List<LeakInfo>): List<LeakInfo> {
        val newLeaks = leaks.filter { !isInBaseline(it) }
        val suppressed = leaks.size - newLeaks.size
        if (suppressed > 0) {
            logger.info("LeakLens: Suppressed $suppressed baseline leak(s)")
        }
        return newLeaks
    }

    fun getBaselineCount(): Int = baselineSignatures.size

    companion object {
        fun getInstance(project: Project): LeakBaselineManager =
            project.getService(LeakBaselineManager::class.java)
    }
}

