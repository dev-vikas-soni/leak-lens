package com.github.devvikassoni.leaklens.monitoring

import com.github.devvikassoni.leaklens.services.AdbHeapDumpService
import com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service responsible for real-time device memory monitoring.
 *
 * Periodically queries device heap statistics using `adb shell dumpsys meminfo`
 * and automatically triggers a heap dump when thresholds are exceeded.
 */
@Service(Service.Level.PROJECT)
class DeviceMemoryMonitor(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val scope get() = LeakLensProjectService.getInstance(project).scope
    private var monitorJob: Job? = null
    private var isMonitoring = false

    enum class Status { DISCONNECTED, CONNECTED, ERROR }

    private val _status = MutableStateFlow(Status.DISCONNECTED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _memorySnapshots = MutableStateFlow<List<MemorySnapshot>>(emptyList())
    val memorySnapshots: StateFlow<List<MemorySnapshot>> = _memorySnapshots.asStateFlow()

    private val _currentMemory = MutableStateFlow<MemorySnapshot?>(null)
    val currentMemory: StateFlow<MemorySnapshot?> = _currentMemory.asStateFlow()

    data class MemorySnapshot(
        val timestamp: Long = System.currentTimeMillis(),
        val totalPss: Long = 0, // KB
        val javaHeap: Long = 0, // KB
        val nativeHeap: Long = 0, // KB
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
        _status.value = Status.CONNECTED
        _lastError.value = null

        monitorJob = scope.launch {
            var consecutiveFailures = 0
            while (isActive && isMonitoring) {
                try {
                    val snapshot = queryMemoryInfo(deviceSerial, packageName)
                    if (snapshot != null) {
                        consecutiveFailures = 0
                        _status.value = Status.CONNECTED
                        _currentMemory.value = snapshot
                        _memorySnapshots.value = (_memorySnapshots.value + snapshot).takeLast(360)
                        // ~30 min at 5s

                        // Auto-trigger heap dump if threshold exceeded
                        checkThresholds(snapshot, deviceSerial, packageName)
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= 3) {
                            _status.value = Status.ERROR
                            val message =
                                "Failed to query memory info repeatedly for '$packageName'. " +
                                        "Ensure the app is running and debuggable."
                            _lastError.value = message
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        logger.warn("LeakLens: Memory monitor error", e)
                        _status.value = Status.ERROR
                        _lastError.value = e.message
                    }
                }
                delay(intervalMs)
            }
        }

        logger.info("LeakLens: Started monitoring $packageName on ${deviceSerial ?: "default device"}")
    }

    fun stopMonitoring() {
        isMonitoring = false
        _status.value = Status.DISCONNECTED
        monitorJob?.cancel()
        monitorJob = null
        logger.info("LeakLens: Stopped monitoring")
    }

    override fun dispose() {
        stopMonitoring()
    }

    fun isActive(): Boolean = isMonitoring

    private fun queryMemoryInfo(deviceSerial: String?, packageName: String): MemorySnapshot? {
        val adbService = AdbHeapDumpService.getInstance(project)
        val adbPath = adbService.getAdbExecutable()

        val commandLine = GeneralCommandLine(adbPath).apply {
            if (!deviceSerial.isNullOrBlank()) {
                addParameter("-s")
                addParameter(deviceSerial)
            }
            addParameters("shell", "dumpsys", "meminfo", packageName)
        }

        return try {
            val handler = CapturingProcessHandler(commandLine)
            val result = handler.runProcess(15000) // Increase to 15 second timeout for dumpsys
            if (result.isTimeout) {
                logger.warn("LeakLens: dumpsys meminfo timed out for $packageName")
                null
            } else if (result.exitCode != 0) {
                logger.warn("LeakLens: dumpsys meminfo failed (exit ${result.exitCode}): ${result.stderr}")
                null
            } else if (result.stdout.isBlank()) {
                logger.warn("LeakLens: dumpsys meminfo returned empty output for $packageName")
                null
            } else {
                parseMemInfo(result.stdout, packageName)
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: Error querying memory info for $packageName", e)
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

                    lower.contains("activities:") ->
                        activities =
                            extractFirstNumber(trimmed).toInt()
                }
            }

            // Fallback: parse TOTAL line format "TOTAL    12345    ..."
            if (totalPss == 0L) {
                val totalLine = output.lines().find { it.trim().startsWith("TOTAL") }
                if (totalLine != null) {
                    totalPss = Regex("""\d+""").findAll(totalLine)
                        .firstOrNull()?.value?.toLongOrNull() ?: 0L
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

    private fun checkThresholds(
        snapshot: MemorySnapshot,
        deviceSerial: String?,
        packageName: String
    ) {
        val settings = LeakLensSettingsState.getInstance(project)
        val threshold = settings.autoHeapDumpThresholdMb

        if (threshold <= 0) return // disabled

        val javaHeapMb = snapshot.javaHeap / 1024
        if (javaHeapMb > threshold) {
            val logMsg = "Java heap ($javaHeapMb MB) exceeds threshold ($threshold MB). " +
                    "Auto-triggering heap dump."
            logger.warn("LeakLens: $logMsg")

            val message = "Java heap ($javaHeapMb MB) exceeds threshold ($threshold MB). " +
                    "Auto-capturing heap dump..."

            NotificationGroupManager.getInstance()
                .getNotificationGroup("LeakLens Notifications")
                .createNotification(
                    "LeakLens",
                    message,
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
