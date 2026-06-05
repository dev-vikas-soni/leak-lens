package com.github.devvikassoni.leaklens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors logcat for LeakCanary's heap dump notifications.
 * When LeakCanary dumps heap, it logs a message like:
 * "D/LeakCanary: Heap dumped to /data/user/0/.../leakcanary/2024-01-01_12-00-00_000.hprof"
 */
@Service(Service.Level.PROJECT)
class LogcatHeapDumpListener(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val isListening = AtomicBoolean(false)
    private var logcatProcess: Process? = null
    private var listenerThread: Thread? = null

    var onHeapDumpDetected: ((deviceSerial: String?, hprofPath: String) -> Unit)? = null

    fun startListening(deviceSerial: String? = null) {
        if (!AdbHeapDumpService.getInstance(project).isAdbAvailable()) {
            logger.warn("LeakLens: ADB not found. Logcat listener cannot start.")
            return
        }

        if (isListening.getAndSet(true)) {
            logger.info("LeakLens: Logcat listener already running")
            return
        }

        listenerThread = Thread({
            try {
                // If serial is null, try to find a device
                val actualSerial = deviceSerial ?: AdbHeapDumpService.getInstance(project).listDevices().firstOrNull()
                
                val command = mutableListOf("adb").apply {
                    if (actualSerial != null) {
                        add("-s")
                        add(actualSerial)
                    }
                    add("logcat")
                    add("-s")
                    add("LeakCanary:D")
                    add("--format=brief")
                }

                logger.info("LeakLens: Starting logcat listener for ${actualSerial ?: "default device"}")

                logcatProcess = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))
                var line: String? = null

                while (isListening.get() && reader.readLine().also { line = it } != null) {
                    line?.let { processLogcatLine(it, deviceSerial) }
                }
            } catch (e: Exception) {
                if (isListening.get()) {
                    logger.error("LeakLens: Logcat listener error", e)
                }
            } finally {
                isListening.set(false)
            }
        }, "LeakLens-LogcatListener").apply {
            isDaemon = true
            start()
        }

        logger.info("LeakLens: Logcat listener started for device: ${deviceSerial ?: "default"}")
    }

    fun stopListening() {
        isListening.set(false)
        logcatProcess?.destroyForcibly()
        logcatProcess = null
        listenerThread?.interrupt()
        listenerThread = null
        logger.info("LeakLens: Logcat listener stopped")
    }

    override fun dispose() {
        stopListening()
    }

    fun isActive(): Boolean = isListening.get()

    private fun processLogcatLine(line: String, deviceSerial: String?) {
        // LeakCanary logs: "Heap dumped to /path/to/file.hprof"
        // Also handles: "D/LeakCanary: Heap dumped to ..."
        val heapDumpPattern = Regex("""Heap dumped to\s+(/.+\.hprof)""")
        val match = heapDumpPattern.find(line)

        if (match != null) {
            val hprofPath = match.groupValues[1].trim()
            logger.info("LeakLens: Detected heap dump at: $hprofPath")
            
            // Notify coordinator to pull and analyze
            // We should use a PooledThread to not block the listener
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                onHeapDumpDetected?.invoke(deviceSerial, hprofPath)
            }
        }

        // Also detect: "1 retained objects, dumping heap"
        // And: "Analysis done: X leaks"
        if (line.contains("retained objects") || line.contains("dumping heap")) {
            logger.info("LeakLens: LeakCanary activity detected: $line")
        }
    }

    companion object {
        fun getInstance(project: Project): LogcatHeapDumpListener =
            project.getService(LogcatHeapDumpListener::class.java)
    }
}

