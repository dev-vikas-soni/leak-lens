package com.github.devvikassoni.leaklens.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors logcat for LeakCanary's heap dump notifications.
 * When LeakCanary dumps heap, it logs a message like:
 * "D/LeakCanary: Heap dumped to /data/user/0/.../leakcanary/2024-01-01_12-00-00_000.hprof"
 *
 * Modernized to use GeneralCommandLine and OSProcessHandler per JetBrains guidelines.
 */
@Service(Service.Level.PROJECT)
class LogcatHeapDumpListener(private val project: Project) : Disposable {

    private val logger = thisLogger()
    private val isListening = AtomicBoolean(false)
    private var processHandler: OSProcessHandler? = null

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

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val actualSerial = deviceSerial ?: AdbHeapDumpService.getInstance(project).listDevices().firstOrNull()
                val adbService = AdbHeapDumpService.getInstance(project)
                val adbPath = adbService.getAdbExecutable()

                val commandLine = GeneralCommandLine(adbPath).apply {
                    if (actualSerial != null) {
                        addParameters("-s", actualSerial)
                    }
                    addParameters("logcat", "-s", "LeakCanary:D", "--format=brief")
                }

                logger.info("LeakLens: Starting logcat process listener for ${actualSerial ?: "default device"}")

                processHandler = OSProcessHandler(commandLine)
                processHandler?.addProcessListener(object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        val line = event.text
                        if (line.isNotBlank()) {
                            processLogcatLine(line, actualSerial)
                        }
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        isListening.set(false)
                        logger.info("LeakLens: Logcat process terminated.")
                    }
                })

                processHandler?.startNotify()
            } catch (e: Exception) {
                isListening.set(false)
                logger.error("LeakLens: Logcat listener error during startup", e)
            }
        }

        logger.info("LeakLens: Logcat listener initialized for device: ${deviceSerial ?: "default"}")
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
        val heapDumpPattern = Regex("""Heap dumped to\s+(/.+\.hprof)""")
        val match = heapDumpPattern.find(line)

        if (match != null) {
            val hprofPath = match.groupValues[1].trim()
            logger.info("LeakLens: Detected heap dump at: $hprofPath")
            
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                onHeapDumpDetected?.invoke(deviceSerial, hprofPath)
            }
        }

        if (line.contains("retained objects") || line.contains("dumping heap")) {
            logger.info("LeakLens: LeakCanary activity detected: ${line.trim()}")
        }
    }

    companion object {
        fun getInstance(project: Project): LogcatHeapDumpListener =
            project.getService(LogcatHeapDumpListener::class.java)
    }
}
