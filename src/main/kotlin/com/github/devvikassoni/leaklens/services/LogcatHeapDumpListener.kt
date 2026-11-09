package com.github.devvikassoni.leaklens.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors logcat for LeakCanary's heap dump notifications.
 *
 * When LeakCanary dumps a heap on the device, it logs a specific message containing
 * the path to the .hprof file. This listener captures that path and triggers
 * the local analysis pipeline.
 */
@Service(Service.Level.PROJECT)
class LogcatHeapDumpListener(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val isListening = AtomicBoolean(false)

    @Volatile
    private var processHandler: OSProcessHandler? = null
    private val seenDumpPaths = ConcurrentHashMap.newKeySet<String>()

    var onHeapDumpDetected: ((deviceSerial: String?, hprofPath: String) -> Unit)? = null

    /**
     * Starts listening asynchronously. Safe to call from the EDT: ADB discovery and process
     * startup run on a pooled thread.
     */
    fun startListening(deviceSerial: String? = null) {
        if (isListening.getAndSet(true)) {
            logger.info("LeakLens: Logcat listener already running")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val adbService = AdbHeapDumpService.getInstance(project)
                if (!adbService.isAdbAvailable()) {
                    isListening.set(false)
                    logger.warn("LeakLens: ADB not found. Logcat listener cannot start.")
                    notifyAdbMissing()
                    return@executeOnPooledThread
                }

                val actualSerial = deviceSerial ?: adbService.listDevices().firstOrNull()
                val adbPath = adbService.getAdbExecutable()
                // Without -T, logcat replays its whole ring buffer and re-analyzes old dumps
                val since = getDeviceEpochSeconds(adbPath, actualSerial)?.let { "$it.000" } ?: "1"

                val commandLine = GeneralCommandLine(adbPath).apply {
                    if (actualSerial != null) {
                        addParameters("-s", actualSerial)
                    }
                    addParameters("logcat", "-T", since, "-s", "LeakCanary:D", "--format=brief")
                }

                logger.info("LeakLens: Starting logcat process listener for ${actualSerial ?: "default device"}")

                val handler = OSProcessHandler(commandLine)
                handler.addProcessListener(object : ProcessListener {
                    override fun startNotified(event: ProcessEvent) {}
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        val line = event.text
                        if (line.isNotBlank()) {
                            processLogcatLine(line, actualSerial)
                        }
                    }
                    override fun processWillTerminate(
                        event: ProcessEvent,
                        willBeDestroyed: Boolean
                    ) {
                    }
                    override fun processTerminated(event: ProcessEvent) {
                        if (processHandler === handler) {
                            processHandler = null
                            isListening.set(false)
                        }
                        logger.info("LeakLens: Logcat process terminated.")
                    }
                })

                processHandler = handler
                handler.startNotify()
                // stopListening() may have run while we were starting up
                if (!isListening.get()) {
                    handler.destroyProcess()
                }
            } catch (e: Exception) {
                isListening.set(false)
                logger.warn("LeakLens: Logcat listener error during startup", e)
            }
        }
    }

    private fun getDeviceEpochSeconds(adbPath: String, serial: String?): Long? {
        val commandLine = GeneralCommandLine(adbPath).apply {
            if (serial != null) addParameters("-s", serial)
            addParameters("shell", "date", "+%s")
        }
        return try {
            val result = CapturingProcessHandler(commandLine).runProcess(DEVICE_TIME_TIMEOUT_MS)
            if (result.isTimeout || result.exitCode != 0) null else result.stdout.trim().toLongOrNull()
        } catch (e: Exception) {
            logger.debug("LeakLens: Could not read device time: ${e.message}")
            null
        }
    }

    private fun notifyAdbMissing() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LeakLens Notifications")
            .createNotification(
                "LeakLens",
                "Auto-detect could not start: adb was not found. Configure the Android SDK or set ANDROID_HOME.",
                NotificationType.WARNING
            )
            .notify(project)
    }

    fun stopListening() {
        isListening.set(false)
        processHandler?.destroyProcess()
        processHandler = null
        logger.info("LeakLens: Logcat listener stopped")
    }

    override fun dispose() {
        stopListening()
    }

    fun isActive(): Boolean = isListening.get()

    private fun processLogcatLine(line: String, deviceSerial: String?) {
        val match = HEAP_DUMP_PATTERN.find(line)

        if (match != null) {
            val hprofPath = match.groupValues[1].trim()
            if (!seenDumpPaths.add(hprofPath)) return
            logger.info("LeakLens: Detected heap dump at: $hprofPath")

            ApplicationManager.getApplication().executeOnPooledThread {
                onHeapDumpDetected?.invoke(deviceSerial, hprofPath)
            }
        }

        if (line.contains("retained objects") || line.contains("dumping heap")) {
            logger.info("LeakLens: LeakCanary activity detected: ${line.trim()}")
        }
    }

    companion object {
        private val HEAP_DUMP_PATTERN = Regex("""Heap dumped to\s+(/.+\.hprof)""")
        private const val DEVICE_TIME_TIMEOUT_MS = 5_000
        fun getInstance(project: Project): LogcatHeapDumpListener =
            project.getService(LogcatHeapDumpListener::class.java)
    }
}
