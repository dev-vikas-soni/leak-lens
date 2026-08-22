package com.github.devvikassoni.leaklens.deobfuscation

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Deobfuscation support for R8/ProGuard obfuscated heap dumps.
 * Maps obfuscated class/method names back to original symbols using mapping.txt.
 */
@Service(Service.Level.PROJECT)
class DeobfuscationService(private val project: Project) {

    private val logger = thisLogger()
    private var mappings: Map<String, String> = emptyMap() // obfuscated -> original
    private var reverseMappings: Map<String, String> = emptyMap() // original -> obfuscated

    /**
     * Load a ProGuard/R8 mapping file.
     */
    fun loadMappingFile(mappingFile: File): Boolean {
        return try {
            val map = mutableMapOf<String, String>()
            var currentOriginalClass = ""

            mappingFile.forEachLine { line ->
                when {
                    // Class mapping: "original.ClassName -> obfuscated.Name:"
                    line.endsWith(":") && line.contains(" -> ") -> {
                        val parts = line.removeSuffix(":").split(" -> ")
                        if (parts.size == 2) {
                            val original = parts[0].trim()
                            val obfuscated = parts[1].trim()
                            map[obfuscated] = original
                            currentOriginalClass = original
                        }
                    }
                    // Field/method mapping: "    type originalName -> obfuscatedName"
                    line.startsWith("    ") && line.contains(" -> ") -> {
                        val trimmed = line.trim()
                        val parts = trimmed.split(" -> ")
                        if (parts.size == 2) {
                            val obfuscatedMember = parts[1].trim()
                            val originalPart = parts[0].trim()
                            // Extract original member name (after last space)
                            val originalMember = originalPart.substringAfterLast(" ").substringBefore("(")
                            map["$currentOriginalClass.$obfuscatedMember"] = "$currentOriginalClass.$originalMember"
                        }
                    }
                }
            }

            mappings = map
            reverseMappings = map.entries.associate { (k, v) -> v to k }
            logger.info("LeakLens: Loaded ${mappings.size} deobfuscation mappings from ${mappingFile.name}")
            true
        } catch (e: Exception) {
            logger.error("LeakLens: Failed to load mapping file", e)
            false
        }
    }

    /**
     * Auto-detect mapping.txt from project build output.
     */
    fun autoDetectMappingFile(): File? {
        val basePath = project.basePath ?: return null
        val candidates = listOf(
            "app/build/outputs/mapping/release/mapping.txt",
            "app/build/outputs/mapping/debug/mapping.txt",
            "build/outputs/mapping/release/mapping.txt"
        )

        for (candidate in candidates) {
            val file = File(basePath, candidate)
            if (file.exists()) {
                logger.info("LeakLens: Auto-detected mapping file at $candidate")
                return file
            }
        }
        return null
    }

    /**
     * Deobfuscate a fully qualified class name.
     */
    fun deobfuscateClassName(obfuscatedName: String): String {
        return mappings[obfuscatedName] ?: obfuscatedName
    }

    /**
     * Deobfuscate a full leak trace string.
     */
    fun deobfuscateTrace(trace: String): String {
        var result = trace
        for ((obfuscated, original) in mappings) {
            if (obfuscated.contains(".") && !obfuscated.contains(" ")) {
                result = result.replace(obfuscated, original)
            }
        }
        return result
    }

    fun hasMappings(): Boolean = mappings.isNotEmpty()

    fun getMappingCount(): Int = mappings.size

    companion object {
        fun getInstance(project: Project): DeobfuscationService =
            project.getService(DeobfuscationService::class.java)
    }
}
