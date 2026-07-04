package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.compat.AdbFacade
import com.github.devvikassoni.leaklens.compat.DeviceFacade
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with the Android Debug Bridge (ADB).
 *
 * This service provides high-level operations for device discovery, process management,
 * and heap dump capture. It abstracts the complexities of direct shell execution and
 * ensures compatibility across varying versions of the Android SDK and the IDE platform.
 */
@Service(Service.Level.PROJECT)
class AdbHeapDumpService(private val project: Project) : Disposable {

    private val logger = thisLogger()

    fun getAdbExecutable(): String = AdbFacade.findAdb(project)

    private fun getDebugBridge(): Any? = AdbFacade.getDebugBridge(project)

    fun isAdbAvailable(): Boolean {
        val exe = getAdbExecutable()
        return try {
            val process = ProcessBuilder(exe, "version").start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun pullHeapDump(deviceSerial: String?, remotePath: String): File? {
        return try {
            val tempFile = File.createTempFile("leaklens_heap_", ".hprof")
            // We manage lifecycle via LeakAnalysisCoordinator or manual cleanup
            // instead of deleteOnExit() to prevent JVM hook bloat.

            val adbCommand = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("pull")
                add(remotePath)
                add(tempFile.absolutePath)
            }

            val process = ProcessBuilder(adbCommand).start()
            if (process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) {
                tempFile
            } else {
                logger.warn("LeakLens: adb pull failed for $remotePath (exit=${process.exitValue()})")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            logger.warn("LeakLens: pullHeapDump error", e)
            null
        }
    }

    /**
     * Triggers a heap dump for the specified package on the given device.
     *
     * @param deviceSerial The serial number of the target device, or null for the default device.
     * @param packageName The application package to dump.
     * @return The path to the generated .hprof file on the device, or null if the operation failed.
     */
    fun triggerHeapDump(deviceSerial: String?, packageName: String): String? {
        return try {
            val remotePath = "/data/local/tmp/leaklens_${System.currentTimeMillis()}.hprof"
            val adbCommand = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                addAll(listOf("shell", "am", "dumpheap", packageName, remotePath))
            }

            val process = ProcessBuilder(adbCommand).start()
            if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
                Thread.sleep(3000)
                remotePath
            } else {
                logger.warn("LeakLens: am dumpheap failed (exit=${process.exitValue()})")
                null
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            logger.warn("LeakLens: triggerHeapDump error", e)
            null
        }
    }

    fun listDevices(): List<String> {
        try {
            val bridge = getDebugBridge()
            if (bridge != null) {
                val devices = bridge.javaClass.getMethod("getDevices").invoke(bridge) as? Array<*>
                if (!devices.isNullOrEmpty()) {
                    return devices.mapNotNull { device ->
                        if (device != null && DeviceFacade.isOnline(device)) {
                            DeviceFacade.getSerialNumber(device)
                        } else null
                    }
                }
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            logger.debug("LeakLens: Could not list devices via debug bridge, falling back to adb: ${e.message}")
        }

        return try {
            val process = ProcessBuilder(getAdbExecutable(), "devices").start()
            process.inputStream.bufferedReader().readText().lines().drop(1)
                .filter { it.contains("\tdevice") }
                .map { it.split("\t").first() }
        } catch (e: Exception) {
            logger.warn("LeakLens: listDevices fallback failed", e)
            emptyList()
        }
    }

    fun listDebuggableProcesses(deviceSerial: String?): List<String> {
        try {
            val bridge = getDebugBridge()
            if (bridge != null) {
                val devicesMethod = bridge.javaClass.getMethod("getDevices")
                val devices = devicesMethod.invoke(bridge) as? Array<*>
                val device = devices?.find { d ->
                    try {
                        DeviceFacade.getSerialNumber(d ?: return@find false) == deviceSerial
                    } catch (_: Exception) {
                        false
                    }
                } ?: devices?.firstOrNull()

                if (device != null) {
                    val clients =
                        device.javaClass.getMethod("getClients").invoke(device) as? Array<*>
                    if (!clients.isNullOrEmpty()) {
                        return clients.mapNotNull { client ->
                            val data = client?.javaClass?.getMethod("getClientData")?.invoke(client)
                            if (data != null) DeviceFacade.getProcessName(data) else null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            logger.debug("LeakLens: Could not list processes via debug bridge: ${e.message}")
        }

        // Fallback: read from jdwp (No deadlock version)
        return try {
            val command = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s"); add(deviceSerial)
                }
                add("jdwp")
            }
            val process = ProcessBuilder(command).redirectErrorStream(true).start()

            // jdwp doesn't terminate, read for a brief moment then kill
            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1000) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    output.append(line).append("\n")
                } else {
                    Thread.sleep(100)
                }
            }
            process.destroyForcibly()

            val pids = output.toString().lines()
                .filter { it.isNotBlank() }
                .distinct()

            pids.mapNotNull { pid ->
                try {
                    val resolveCommand = mutableListOf(getAdbExecutable()).apply {
                        if (deviceSerial != null) {
                            add("-s"); add(deviceSerial)
                        }
                        addAll(listOf("shell", "cat", "/proc/$pid/cmdline"))
                    }
                    val resolveProcess = ProcessBuilder(resolveCommand).start()
                    resolveProcess.waitFor(5, TimeUnit.SECONDS)
                    resolveProcess.inputStream.bufferedReader().readText().trim()
                        .replace("\u0000", "").ifBlank { null }
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: listDebuggableProcesses fallback failed", e)
            emptyList()
        }
    }

    fun deleteRemoteFile(deviceSerial: String?, remotePath: String) {
        try {
            val command = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) {
                    add("-s"); add(deviceSerial)
                }
                addAll(listOf("shell", "rm", "-f", remotePath))
            }
            ProcessBuilder(command).start().waitFor(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.debug("LeakLens: deleteRemoteFile failed for $remotePath: ${e.message}")
        }
    }

    companion object {
        fun getInstance(project: Project): AdbHeapDumpService =
            project.getService(AdbHeapDumpService::class.java)
    }

    override fun dispose() {}
}
