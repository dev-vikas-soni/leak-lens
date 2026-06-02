package com.github.devvikassoni.leaklens.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class AdbHeapDumpService(private val project: Project) {

    private val logger = thisLogger()

    /**
     * Pull a heap dump file from a connected device.
     */
    fun pullHeapDump(deviceSerial: String?, remotePath: String): File? {
        return try {
            val tempFile = File.createTempFile("leaklens_heap_", ".hprof")
            tempFile.deleteOnExit()

            val adbCommand = mutableListOf("adb").apply {
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

            val exitCode = process.waitFor()
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

            val adbCommand = mutableListOf("adb").apply {
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

            val exitCode = process.waitFor()
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
        return try {
            val process = ProcessBuilder("adb", "devices")
                .redirectErrorStream(true)
                .start()

            process.waitFor()
            val output = process.inputStream.bufferedReader().readText()

            output.lines()
                .drop(1)
                .filter { it.contains("\tdevice") }
                .map { it.split("\t").first() }
        } catch (e: Exception) {
            logger.error("LeakLens: Error listing devices", e)
            emptyList()
        }
    }

    /**
     * Delete a file on the remote device.
     */
    fun deleteRemoteFile(deviceSerial: String?, remotePath: String) {
        try {
            val command = mutableListOf("adb").apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("shell")
                add("rm")
                add("-f")
                add(remotePath)
            }
            ProcessBuilder(command).redirectErrorStream(true).start().waitFor()
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to delete remote file: $remotePath", e)
        }
    }

    /**
     * List debuggable processes on the device.
     */
    fun listDebuggableProcesses(deviceSerial: String?): List<String> {
        return try {
            val command = mutableListOf("adb").apply {
                if (deviceSerial != null) {
                    add("-s")
                    add(deviceSerial)
                }
                add("jdwp")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // jdwp doesn't terminate, read for a brief moment
            Thread.sleep(500)
            process.destroyForcibly()

            val pids = process.inputStream.bufferedReader().readText()
                .lines()
                .filter { it.isNotBlank() }

            // Resolve PIDs to package names
            pids.mapNotNull { pid -> resolveProcessName(deviceSerial, pid.trim()) }
        } catch (e: Exception) {
            logger.error("LeakLens: Error listing processes", e)
            emptyList()
        }
    }

    private fun resolveProcessName(deviceSerial: String?, pid: String): String? {
        return try {
            val command = mutableListOf("adb").apply {
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

            process.waitFor()
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
}
