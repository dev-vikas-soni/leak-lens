package com.github.devvikassoni.leaklens.monitoring

import com.github.devvikassoni.leaklens.services.AdbHeapDumpService
import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask

/**
 * Phase 6: Real-time device monitoring service.
 * Periodically queries device heap stats via `adb shell dumpsys meminfo <package>`
 * and auto-triggers heap dump when retained object count exceeds threshold.
 */
@Service(Service.Level.PROJECT)
class DeviceMemoryMonitor(private val project: Project) {

    private val logger = thisLogger()
    private var monitorTimer: Timer? = null
    private var isMonitoring = false

    private val _memorySnapshots = MutableStateFlow<List<MemorySnapshot>>(emptyList())
    val memorySnapshots: StateFlow<List<MemorySnapshot>> = _memorySnapshots.asStateFlow()

    private val _currentMemory = MutableStateFlow<MemorySnapshot?>(null)
    val currentMemory: StateFlow<MemorySnapshot?> = _currentMemory.asStateFlow()

    data class MemorySnapshot(
        val timestamp: Long = System.currentTimeMillis(),
        val totalPss: Long = 0,           // KB
        val javaHeap: Long = 0,           // KB
        val nativeHeap: Long = 0,         // KB
        val objects: Int = 0,
        val activities: Int = 0,
        val viewCount: Int = 0,
        val packageName: String = ""
    )

    fun startMonitoring(deviceSerial: String?, packageName: String, intervalMs: Long = 5000) {
        if (isMonitoring) {
            logger.info("LeakLens: Already monitoring")
            return
        }

        isMonitoring = true
        _memorySnapshots.value = emptyList()

        monitorTimer = Timer("LeakLens-MemoryMonitor", true)
        monitorTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val snapshot = queryMemoryInfo(deviceSerial, packageName)
                    if (snapshot != null) {
                        _currentMemory.value = snapshot
                        _memorySnapshots.value = (_memorySnapshots.value + snapshot).takeLast(360) // ~30 min at 5s

                        // Auto-trigger heap dump if threshold exceeded
                        checkThresholds(snapshot, deviceSerial, packageName)
                    }
                } catch (e: Exception) {
                    logger.warn("LeakLens: Memory monitor error", e)
                }
            }
        }, 0, intervalMs)

        logger.info("LeakLens: Started monitoring $packageName on ${deviceSerial ?: "default device"}")
    }

    fun stopMonitoring() {
        monitorTimer?.cancel()
        monitorTimer = null
        isMonitoring = false
        logger.info("LeakLens: Stopped monitoring")
    }

    fun isActive(): Boolean = isMonitoring

    private fun queryMemoryInfo(deviceSerial: String?, packageName: String): MemorySnapshot? {
        val command = buildList {
            add("adb")
            if (deviceSerial != null) { add("-s"); add(deviceSerial) }
            add("shell")
            add("dumpsys")
            add("meminfo")
            add(packageName)
        }

        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        return parseMemInfo(output, packageName)
    }

    private fun parseMemInfo(output: String, packageName: String): MemorySnapshot? {
        try {
            var totalPss = 0L
            var javaHeap = 0L
            var nativeHeap = 0L
            var viewCount = 0
            var activities = 0

            for (line in output.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("TOTAL PSS:") || trimmed.startsWith("TOTAL:") -> {
                        totalPss = extractFirstNumber(trimmed)
                    }
                    trimmed.contains("Java Heap:") -> javaHeap = extractFirstNumber(trimmed)
                    trimmed.contains("Native Heap:") -> nativeHeap = extractFirstNumber(trimmed)
                    trimmed.contains("ViewRootImpl:") || trimmed.contains("Views:") -> {
                        viewCount = extractFirstNumber(trimmed).toInt()
                    }
                    trimmed.contains("Activities:") -> activities = extractFirstNumber(trimmed).toInt()
                }
            }

            // Fallback: parse TOTAL line format "TOTAL    12345    ..."
            if (totalPss == 0L) {
                val totalLine = output.lines().find { it.trim().startsWith("TOTAL") }
                if (totalLine != null) {
                    totalPss = Regex("""\d+""").findAll(totalLine).firstOrNull()?.value?.toLongOrNull() ?: 0L
                }
            }

            return MemorySnapshot(
                totalPss = totalPss,
                javaHeap = javaHeap,
                nativeHeap = nativeHeap,
                viewCount = viewCount,
                activities = activities,
                packageName = packageName
            )
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to parse meminfo", e)
            return null
        }
    }

    private fun extractFirstNumber(line: String): Long {
        return Regex("""\d+""").find(line)?.value?.toLongOrNull() ?: 0L
    }

    private fun checkThresholds(snapshot: MemorySnapshot, deviceSerial: String?, packageName: String) {
        val settings = LeakLensSettingsState.getInstance(project)
        val threshold = settings.autoHeapDumpThresholdMb

        if (threshold <= 0) return // disabled

        val javaHeapMb = snapshot.javaHeap / 1024
        if (javaHeapMb > threshold) {
            logger.warn("LeakLens: Java heap ($javaHeapMb MB) exceeds threshold ($threshold MB). Auto-triggering heap dump.")

            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification(
                    "LeakLens",
                    "Java heap ($javaHeapMb MB) exceeds threshold ($threshold MB). Auto-capturing heap dump...",
                    NotificationType.WARNING
                )
                .notify(project)

            // Trigger analysis
            LeakAnalysisCoordinator.getInstance(project).triggerAndAnalyze(deviceSerial, packageName)

            // Pause monitoring briefly to avoid repeated triggers
            stopMonitoring()
        }
    }

    companion object {
        fun getInstance(project: Project): DeviceMemoryMonitor =
            project.getService(DeviceMemoryMonitor::class.java)
    }
}

