package com.github.devvikassoni.leaklens.baseline

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages the leak baseline, allowing known issues to be suppressed from future analysis.
 *
 * Stores suppressed/accepted leak signatures in a VCS-tracked file (`leak-baseline.json` by default,
 * configurable via [LeakLensSettingsState.baselineFilePath]).
 * New leaks not in the baseline trigger warnings, while baseline leaks are filtered out.
 *
 * The in-memory map is the source of truth: every write serializes the full map, so concurrent
 * additions can never be lost or interleaved in the file.
 */
@Service(Service.Level.PROJECT)
class LeakBaselineManager(private val project: Project) {

    private data class Entry(val signature: String, val className: String, val description: String)

    private val logger = thisLogger()
    private val entries = LinkedHashMap<String, Entry>()
    private val writeLock = Any()

    internal val baselineFile: File
        get() {
            val configured = LeakLensSettingsState.getInstance(project).baselineFilePath
                .ifBlank { DEFAULT_FILE_NAME }
            val file = File(configured)
            return if (file.isAbsolute) {
                file
            } else {
                File(project.basePath ?: System.getProperty("java.io.tmpdir"), configured)
            }
        }

    init {
        loadBaseline()
    }

    fun loadBaseline() {
        val loaded = try {
            val file = baselineFile
            if (file.exists()) parse(file.readText()) else emptyList()
        } catch (e: IOException) {
            logger.warn("LeakLens: Failed to read baseline", e)
            return
        } catch (e: JsonParseException) {
            logger.warn("LeakLens: Baseline file is not valid JSON; ignoring it", e)
            return
        } catch (e: IllegalStateException) {
            logger.warn("LeakLens: Baseline file has an unexpected structure; ignoring it", e)
            return
        }
        synchronized(entries) {
            entries.clear()
            loaded.forEach { entries[it.signature] = it }
        }
        logger.info("LeakLens: Loaded ${loaded.size} baseline entries from ${baselineFile.name}")
    }

    /** Replaces the baseline with [leaks]. */
    fun saveBaseline(leaks: List<LeakInfo>) {
        synchronized(entries) {
            entries.clear()
            leaks.forEach { entries[it.signature] = it.toEntry() }
        }
        persistAsync()
        logger.info("LeakLens: Saved ${leaks.size} entries to baseline")
    }

    fun addToBaseline(leak: LeakInfo) {
        synchronized(entries) { entries[leak.signature] = leak.toEntry() }
        persistAsync()
        logger.info("LeakLens: Added ${leak.signature} to baseline")
    }

    fun isInBaseline(leak: LeakInfo): Boolean = synchronized(entries) { leak.signature in entries }

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

    fun getBaselineCount(): Int = synchronized(entries) { entries.size }

    private fun persistAsync() {
        LeakLensProjectService.getInstance(project).scope.launch(Dispatchers.IO) { persist() }
    }

    private fun persist() {
        synchronized(writeLock) {
            // Snapshot at write time so the latest state always wins, regardless of launch order
            val snapshot = synchronized(entries) { entries.values.toList() }
            val target = baselineFile
            try {
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.writeText(serialize(snapshot))
                try {
                    Files.move(
                        tmp.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                logger.warn("LeakLens: Failed to write baseline to ${target.path}", e)
            }
        }
    }

    private fun serialize(snapshot: List<Entry>): String {
        val leaks = JsonArray()
        snapshot.forEach { entry ->
            val item = JsonObject()
            item.addProperty("signature", entry.signature)
            item.addProperty("class", entry.className)
            item.addProperty("description", entry.description)
            leaks.add(item)
        }
        val root = JsonObject()
        root.addProperty("description", "LeakLens baseline - suppressed leak signatures")
        root.addProperty("generatedAt", java.time.Instant.now().toString())
        root.add("leaks", leaks)
        return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n"
    }

    private fun parse(content: String): List<Entry> {
        if (content.isBlank()) return emptyList()
        val leaks = JsonParser.parseString(content).asJsonObject.getAsJsonArray("leaks") ?: return emptyList()
        return leaks.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val signature = obj.get("signature")?.asString ?: return@mapNotNull null
            Entry(
                signature = signature,
                className = obj.get("class")?.asString.orEmpty(),
                description = obj.get("description")?.asString.orEmpty()
            )
        }
    }

    private fun LeakInfo.toEntry() = Entry(signature, retainedObjectClassName, shortDescription)

    companion object {
        private const val DEFAULT_FILE_NAME = "leak-baseline.json"

        fun getInstance(project: Project): LeakBaselineManager =
            project.getService(LeakBaselineManager::class.java)
    }
}
