package com.github.devvikassoni.leaklens.monitoring

import com.github.devvikassoni.leaklens.services.AdbHeapDumpService
import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 6: Real-time device monitoring service.
 * Periodically queries device heap stats via `adb shell dumpsys meminfo <package>`
 * and auto-triggers heap dump when retained object count exceeds threshold.
 *
 * Adheres to JetBrains Platform standards: Uses Coroutines for scheduling
 * and GeneralCommandLine for process management.
 */
@Service(Service.Level.PROJECT)
class DeviceMemoryMonitor(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
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

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    val snapshot = queryMemoryInfo(deviceSerial, packageName)
                    if (snapshot != null) {
                        _currentMemory.value = snapshot
                        _memorySnapshots.value = (_memorySnapshots.value + snapshot).takeLast(360) // ~30 min at 5s

                        // Auto-trigger heap dump if threshold exceeded
                        checkThresholds(snapshot, deviceSerial, packageName)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        logger.warn("LeakLens: Memory monitor error", e)
                    }
                }
                delay(intervalMs)
            }
        }

        logger.info("LeakLens: Started monitoring $packageName on ${deviceSerial ?: "default device"}")
    }

    fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        logger.info("LeakLens: Stopped monitoring")
    }

    override fun dispose() {
        stopMonitoring()
        scope.cancel()
    }

    fun isActive(): Boolean = isMonitoring

    private fun queryMemoryInfo(deviceSerial: String?, packageName: String): MemorySnapshot? {
        val adbService = AdbHeapDumpService.getInstance(project)
        val adbPath = adbService.getAdbExecutable()

        val commandLine = GeneralCommandLine(adbPath).apply {
            if (deviceSerial != null) {
                addParameters("-s", deviceSerial)
            }
            addParameters("shell", "dumpsys", "meminfo", packageName)
        }

        return try {
            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(5000) // 5 second timeout
            if (result.isTimeout) {
                logger.warn("LeakLens: dumpsys meminfo timed out")
                null
            } else if (result.exitCode != 0) {
                logger.warn("LeakLens: dumpsys meminfo failed with exit code ${result.exitCode}: ${result.stderr}")
                null
            } else {
                parseMemInfo(result.stdout, packageName)
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: Error querying memory info", e)
            null
        }
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
                val lower = trimmed.lowercase()
                when {
                    lower.startsWith("total pss:") || lower.startsWith("total:") -> {
                        totalPss = extractFirstNumber(trimmed)
                    }

                    lower.contains("java heap") -> {
                        val num = extractFirstNumber(trimmed)
                        if (num > 0) javaHeap = num
                    }

                    lower.contains("native heap") -> {
                        val num = extractFirstNumber(trimmed)
                        if (num > 0) nativeHeap = num
                    }

                    lower.contains("viewrootimpl") || lower.contains("views:") -> {
                        viewCount = extractFirstNumber(trimmed).toInt()
                    }

                    lower.contains("activities:") -> activities =
                        extractFirstNumber(trimmed).toInt()
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
