package com.github.devvikassoni.leaklens.baseline

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages the leak baseline, allowing known issues to be suppressed from future analysis.
 *
 * Stores suppressed/accepted leak signatures in a VCS-tracked file (`leak-baseline.json`).
 * New leaks not in the baseline trigger warnings, while baseline leaks are filtered out.
 */
@Service(Service.Level.PROJECT)
class LeakBaselineManager(private val project: Project) {

    private val logger = thisLogger()
    private val baselineSignatures = mutableSetOf<String>()

    private val baselineFile: File
        get() = File(project.basePath ?: System.getProperty("java.io.tmpdir"), "leak-baseline.json")

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
        val scope = LeakLensProjectService.getInstance(project).scope
        scope.launch(Dispatchers.IO) {
            baselineFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }
            baselineFile.writeText(json)
        }
        baselineSignatures.clear()
        baselineSignatures.addAll(leaks.map { it.signature })
        logger.info("LeakLens: Saved ${leaks.size} entries to baseline")
    }

    fun addToBaseline(leak: LeakInfo) {
        baselineSignatures.add(leak.signature)
        val scope = LeakLensProjectService.getInstance(project).scope
        scope.launch(Dispatchers.IO) {
            // Re-save full baseline
            if (baselineFile.exists()) {
                var content = baselineFile.readText()
                val newEntry =
                    "    {\"signature\": \"${leak.signature}\", \"class\": \"${leak.retainedObjectClassName}\", \"description\": \"${
                        leak.shortDescription.replace(
                            "\"",
                            "\\\""
                        )
                    }\"}"
                val insertPos = content.lastIndexOf("]")
                if (insertPos > 0) {
                    val isArrayEmpty =
                        content.substring(content.lastIndexOf("[") + 1, insertPos).trim().isEmpty()
                    val prefix = if (isArrayEmpty) "\n" else ",\n"
                    content = content.substring(
                        0,
                        insertPos
                    ) + prefix + newEntry + "\n  " + content.substring(insertPos)
                    baselineFile.writeText(content)
                }
            } else {
                saveBaseline(listOf(leak))
            }
        }
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

