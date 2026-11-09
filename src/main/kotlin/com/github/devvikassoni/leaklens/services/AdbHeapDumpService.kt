package com.github.devvikassoni.leaklens.services

import com.github.devvikassoni.leaklens.compat.AdbFacade
import com.github.devvikassoni.leaklens.compat.DeviceFacade
import com.github.devvikassoni.leaklens.compat.ProgressFacade
import com.github.devvikassoni.leaklens.settings.LeakLensSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
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

    @Volatile
    private var cachedAdbPath: String? = null

    /**
     * Resolves the adb executable. Absolute paths are cached because resolution involves
     * reflection into the Android plugin and file-system probing on every ADB call.
     */
    fun getAdbExecutable(): String {
        cachedAdbPath?.let { if (File(it).isFile) return it }
        val resolved = AdbFacade.findAdb(project)
        cachedAdbPath = resolved.takeIf { File(it).isAbsolute && File(it).isFile }
        return resolved
    }

    private fun getDebugBridge(): Any? = AdbFacade.getDebugBridge(project)

    /** Spawns `adb version`; must not be called on the EDT. */
    fun isAdbAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(getAdbExecutable(), "version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(ADB_VERSION_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            false
        }
    }

    fun pullHeapDump(deviceSerial: String?, remotePath: String): File? {
        val tempFile = try {
            // Lifecycle is managed by LeakAnalysisCoordinator / retention policy instead of
            // deleteOnExit() to prevent JVM shutdown-hook bloat.
            File.createTempFile(SNAPSHOT_PREFIX, SNAPSHOT_SUFFIX)
        } catch (e: java.io.IOException) {
            logger.warn("LeakLens: Could not create local file for heap dump", e)
            return null
        }

        val pulled = runAdbToFile(deviceSerial, listOf("pull", remotePath, tempFile.absolutePath), null) ||
                pullViaRunAs(deviceSerial, remotePath, tempFile)

        return if (pulled && tempFile.length() > 0) {
            enforceRetentionPolicy()
            tempFile
        } else {
            logger.warn("LeakLens: Failed to pull heap dump $remotePath")
            tempFile.delete()
            null
        }
    }

    /**
     * LeakCanary writes dumps into the app's private storage (e.g. `/data/user/0/<pkg>/files/leakcanary`),
     * which `adb pull` cannot read on non-rooted devices. For debuggable apps, `run-as` can stream it.
     */
    private fun pullViaRunAs(deviceSerial: String?, remotePath: String, target: File): Boolean {
        val packageName = PRIVATE_APP_PATH.find(remotePath)?.groupValues?.get(1) ?: return false
        logger.info("LeakLens: adb pull failed, retrying via run-as $packageName")
        val ok = runAdbToFile(deviceSerial, listOf("exec-out", "run-as", packageName, "cat", remotePath), target)
        // exec-out can exit 0 while run-as prints an error (e.g. "package not debuggable") to stdout
        return ok && isHprof(target)
    }

    private fun isHprof(file: File): Boolean = try {
        file.inputStream().use { input ->
            val header = ByteArray(HPROF_MAGIC.length)
            input.read(header) == header.size && String(header, Charsets.US_ASCII) == HPROF_MAGIC
        }
    } catch (_: java.io.IOException) {
        false
    }

    /**
     * Runs an adb command, optionally redirecting stdout to [stdoutTarget].
     * @return true when the command exits with 0 within [PULL_TIMEOUT_SEC].
     */
    private fun runAdbToFile(deviceSerial: String?, args: List<String>, stdoutTarget: File?): Boolean {
        return try {
            val command = mutableListOf(getAdbExecutable()).apply {
                if (deviceSerial != null) addAll(listOf("-s", deviceSerial))
                addAll(args)
            }
            val builder = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.redirectOutput(
                stdoutTarget?.let { ProcessBuilder.Redirect.to(it) } ?: ProcessBuilder.Redirect.DISCARD
            )
            val process = builder.start()
            if (!process.waitFor(PULL_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn("LeakLens: adb ${args.first()} timed out after ${PULL_TIMEOUT_SEC}s")
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            logger.warn("LeakLens: adb ${args.first()} failed", e)
            false
        }
    }

    /**
     * Deletes local .hprof files generated by LeakLens.
     *
     * The temp directory is shared by every open project and IDE instance, so [minAgeMs] lets
     * callers skip recent files that another analysis may still be reading.
     */
    fun clearLocalSnapshots(minAgeMs: Long = 0) {
        try {
            val cutoff = System.currentTimeMillis() - minAgeMs
            val snapshots = listLocalSnapshots().filter { it.lastModified() <= cutoff }

            snapshots.forEach { it.delete() }
            logger.info("LeakLens: Cleared ${snapshots.size} local heap dump snapshots")
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to clear local snapshots", e)
        }
    }

    /**
     * Enforces the local snapshot retention policy based on settings.
     */
    fun enforceRetentionPolicy() {
        try {
            val settings = LeakLensSettingsState.getInstance(project)
            val maxSnapshots = settings.maxLocalSnapshots
            if (maxSnapshots <= 0) return

            val snapshots = listLocalSnapshots().sortedByDescending { it.lastModified() }

            if (snapshots.size > maxSnapshots) {
                val cutoff = System.currentTimeMillis() - IN_USE_GRACE_MS
                val toDelete = snapshots.drop(maxSnapshots).filter { it.lastModified() <= cutoff }
                toDelete.forEach { it.delete() }
                logger.info(
                    "LeakLens: Retained $maxSnapshots snapshots, deleted ${toDelete.size} old snapshots"
                )
            }
        } catch (e: Exception) {
            logger.warn("LeakLens: Failed to enforce retention policy", e)
        }
    }

    private fun listLocalSnapshots(): List<File> =
        File(System.getProperty("java.io.tmpdir")).listFiles { _, name ->
            name.startsWith(SNAPSHOT_PREFIX) && name.endsWith(SNAPSHOT_SUFFIX)
        }?.toList().orEmpty()

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

            val process = ProcessBuilder(adbCommand).redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                logger.warn("LeakLens: am dumpheap timed out for $packageName")
                null
            } else if (process.exitValue() == 0) {
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

    /**
     * Waits until the heap dump at [remotePath] exists and its size has stopped growing.
     * `am dumpheap` can return before the app process has finished writing the HPROF,
     * and pulling early yields a truncated, unparseable dump.
     *
     * @return true once the file is stable, false on timeout.
     */
    fun waitForRemoteFileComplete(
        deviceSerial: String?,
        remotePath: String,
        indicator: ProgressIndicator?,
        timeoutMs: Long = REMOTE_DUMP_TIMEOUT_MS
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastSize = -1L
        var stablePolls = 0
        while (System.currentTimeMillis() < deadline) {
            ProgressFacade.checkCanceled(indicator)
            val size = getRemoteFileSize(deviceSerial, remotePath)
            if (size != null && size > 0 && size == lastSize) {
                stablePolls++
                if (stablePolls >= REQUIRED_STABLE_POLLS) return true
            } else {
                stablePolls = 0
            }
            lastSize = size ?: -1L
            Thread.sleep(REMOTE_POLL_INTERVAL_MS)
        }
        logger.warn("LeakLens: Heap dump at $remotePath did not stabilize within ${timeoutMs}ms")
        return false
    }

    private fun getRemoteFileSize(deviceSerial: String?, remotePath: String): Long? {
        val commandLine = GeneralCommandLine(getAdbExecutable()).apply {
            if (deviceSerial != null) addParameters("-s", deviceSerial)
            addParameters("shell", "stat", "-c", "%s", remotePath)
        }
        return try {
            val result = CapturingProcessHandler(commandLine).runProcess(STAT_TIMEOUT_MS)
            if (result.isTimeout || result.exitCode != 0) null else result.stdout.trim().toLongOrNull()
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            logger.debug("LeakLens: stat failed for $remotePath: ${e.message}")
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
                        } else {
                            null
                        }
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
                    add("-s")
                    add(deviceSerial)
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
                            add("-s")
                            add(deviceSerial)
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
                    add("-s")
                    add(deviceSerial)
                }
                addAll(listOf("shell", "rm", "-f", remotePath))
            }
            ProcessBuilder(command).start().waitFor(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.debug("LeakLens: deleteRemoteFile failed for $remotePath: ${e.message}")
        }
    }

    companion object {
        private const val REMOTE_DUMP_TIMEOUT_MS = 180_000L
        private const val REMOTE_POLL_INTERVAL_MS = 500L
        private const val REQUIRED_STABLE_POLLS = 3
        private const val STAT_TIMEOUT_MS = 10_000
        private const val ADB_VERSION_TIMEOUT_SEC = 5L
        private const val PULL_TIMEOUT_SEC = 300L
        private const val SNAPSHOT_PREFIX = "leaklens_heap_"
        private const val SNAPSHOT_SUFFIX = ".hprof"
        private const val HPROF_MAGIC = "JAVA PROFILE"

        /** Snapshots newer than this may still be under analysis by another project/IDE. */
        const val IN_USE_GRACE_MS = 60 * 60 * 1000L

        private val PRIVATE_APP_PATH = Regex("""^/data/(?:data|user/\d+)/([^/]+)/""")

        fun getInstance(project: Project): AdbHeapDumpService =
            project.getService(AdbHeapDumpService::class.java)
    }

    override fun dispose() {}
}
