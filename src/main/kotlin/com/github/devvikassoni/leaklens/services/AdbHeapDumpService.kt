package com.github.devvikassoni.leaklens.services

import com.android.ddmlib.AndroidDebugBridge
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.File
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class AdbHeapDumpService(private val project: Project) : Disposable {

    private val logger = thisLogger()

    fun getAdbExecutable(): String {
        val adb = AndroidSdkUtils.findAdb(project).adbPath
        return if (adb?.exists() == true) adb.absolutePath else "adb"
    }

    private fun getDebugBridge(): AndroidDebugBridge? {
        var bridge: AndroidDebugBridge? = null
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            bridge = AndroidSdkUtils.getDebugBridge(project)
        } else {
            app.invokeAndWait {
                bridge = AndroidSdkUtils.getDebugBridge(project)
            }
        }
        return bridge
    }

    /**
     * Check if ADB is available.
     */
    fun isAdbAvailable(): Boolean {
        val bridge = getDebugBridge()
        val isBridgeConnected = bridge != null && bridge.isConnected

        return isBridgeConnected || try {
            val process = ProcessBuilder(getAdbExecutable(), "version").start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pull a heap dump file from a connected device.
     */
    fun pullHeapDump(deviceSerial: String?, remotePath: String): File? {
        return try {
            val tempFile = File.createTempFile("leaklens_heap_", ".hprof")
            tempFile.deleteOnExit()

            val adbCommand = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("pull")
                add(remotePath)
                add(tempFile.absolutePath)
            }

            logger.info("LeakLens: Executing: ${adbCommand.joinToString(" ")}")

            val process = ProcessBuilder(adbCommand)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(60, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                logger.error("LeakLens: ADB pull timed out")
                return null
            }
            val exitCode = process.exitValue()
            val output = process.inputStream.bufferedReader().readText()

            if (exitCode == 0) {
                logger.info("LeakLens: Heap dump pulled successfully: ${tempFile.absolutePath}")
                tempFile
            } else {
                logger.error("LeakLens: Failed to pull heap dump: $output")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            logger.error("LeakLens: Error pulling heap dump", e)
            null
        }
    }

    /**
     * Trigger a heap dump on the connected device for the given process.
     */
    fun triggerHeapDump(deviceSerial: String?, packageName: String): String? {
        return try {
            val remotePath = "/data/local/tmp/leaklens_${System.currentTimeMillis()}.hprof"

            val adbCommand = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("shell")
                add("am")
                add("dumpheap")
                add(packageName)
                add(remotePath)
            }

            logger.info("LeakLens: Triggering heap dump: ${adbCommand.joinToString(" ")}")

            val process = ProcessBuilder(adbCommand)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                logger.error("LeakLens: ADB dumpheap timed out")
                return null
            }
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                Thread.sleep(3000)
                remotePath
            } else {
                logger.error("LeakLens: Failed to trigger heap dump")
                null
            }
        } catch (e: Exception) {
            logger.error("LeakLens: Error triggering heap dump", e)
            null
        }
    }

    /**
     * List connected devices.
     */
    fun listDevices(): List<String> {
        // Try using ddmlib first
        try {
            val bridge = getDebugBridge()
            if (bridge != null && bridge.isConnected && bridge.devices.isNotEmpty()) {
                return bridge.devices.asSequence()
                    .filter { it.isOnline }
                    .map { it.serialNumber }
                    .toList()
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: ddmlib failed to list devices, falling back to shell", e)
        }

        // Fallback to shell execution
        return try {
            val process = ProcessBuilder(getAdbExecutable(), "devices")
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(10, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().readText()

            output.lines()
                .drop(1)
                .filter { it.contains("\tdevice") }
                .map { it.split("\t").first() }
        } catch (e: Exception) {
            logger.error("LeakLens: Error listing devices via shell", e)
            emptyList()
        }
    }

    /**
     * Delete a file on the remote device.
     */
    fun deleteRemoteFile(deviceSerial: String?, remotePath: String) {
        try {
            val command = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("shell")
                add("rm")
                add("-f")
                add(remotePath)
            }
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to delete remote file: $remotePath", e)
        }
    }

    /**
     * List debuggable processes on the device.
     */
    fun listDebuggableProcesses(deviceSerial: String?): List<String> {
        // Try ddmlib first
        try {
            val bridge = getDebugBridge()
            if (bridge != null && bridge.isConnected) {
                val device = bridge.devices.find { it.serialNumber == deviceSerial }
                    ?: bridge.devices.firstOrNull()

                if (device != null) {
                    val clients = device.clients
                    if (clients.isNotEmpty()) {
                        return clients.mapNotNull { getProcessNameSafely(it.clientData) }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: ddmlib failed to list processes, falling back to shell", e)
        }

        // Fallback to shell
        return try {
            val command = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("jdwp")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val pids = mutableListOf<String>()
            val reader = process.inputStream.bufferedReader()
            
            // jdwp doesn't terminate, read for a brief moment
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 800) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    pids.add(line.trim())
                } else {
                    Thread.sleep(100)
                }
            }
            process.destroyForcibly()

            // Resolve PIDs to package names
            pids.distinct().mapNotNull { pid -> resolveProcessName(deviceSerial, pid) }
        } catch (e: Exception) {
            logger.error("LeakLens: Error listing processes via shell", e)
            emptyList()
        }
    }

    /**
     * Safely gets the process name from ClientData, handling API differences
     * across Android Studio / IntelliJ versions.
     */
    private fun getProcessNameSafely(data: com.android.ddmlib.ClientData): String? {
        return try {
            // Try getProcessName() via reflection (available in newer ddmlib)
            val method = data.javaClass.getMethod("getProcessName")
            method.invoke(data) as? String
        } catch (e: Exception) {
            // Fallback to getClientDescription() (available in older ddmlib)
            @Suppress("DEPRECATION")
            data.clientDescription
        }
    }

    private fun resolveProcessName(deviceSerial: String?, pid: String): String? {
        return try {
            val command = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("shell")
                add("cat")
                add("/proc/$pid/cmdline")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            val name = process.inputStream.bufferedReader().readText()
                .trim()
                .replace("\u0000", "")

            name.ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun getInstance(project: Project): AdbHeapDumpService =
            project.getService(AdbHeapDumpService::class.java)
    }

    override fun dispose() {
        // Nothing to clean up
    }
}
