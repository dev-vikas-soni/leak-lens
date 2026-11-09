package com.github.devvikassoni.leaklens.compat

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Adaptive facade for ADB operations.
 */
object AdbFacade {
    private val logger = thisLogger()

    /**
     * Resolves the ADB executable path using the best available IDE API.
     */
    fun findAdb(project: Project): String {
        val sdkUtils = ReflectionCache.getClass("org.jetbrains.android.sdk.AndroidSdkUtils")
        if (sdkUtils != null) {
            try {
                // Try findAdb (Ladybug / Meerkat)
                val findAdbMethod =
                    ReflectionCache.getMethod(sdkUtils, "findAdb", Project::class.java)
                if (findAdbMethod != null) {
                    val result = findAdbMethod.invoke(null, project)
                    if (result != null) {
                        resolvePathFromObject(result)?.let { return it }
                    }
                }

                // Try getAdb (Koala / Legacy)
                val getAdbMethod =
                    ReflectionCache.getMethod(sdkUtils, "getAdb", Project::class.java)
                if (getAdbMethod != null) {
                    val result = getAdbMethod.invoke(null, project) as? File
                    if (result?.exists() == true) return result.absolutePath
                }
            } catch (e: Exception) {
                logger.debug("AndroidSdkUtils discovery failed, falling back to PATH")
            }
        }

        findAdbInSdkLocations(project)?.let { return it }

        // Final fallback: rely on the shell PATH
        return "adb"
    }

    /**
     * Looks for `platform-tools/adb` in the usual SDK locations. IDEs launched from the macOS
     * Dock/Finder do not inherit the shell PATH, so a bare "adb" frequently fails there.
     */
    private fun findAdbInSdkLocations(project: Project): String? {
        val adbName = if (System.getProperty("os.name").lowercase().contains("win")) "adb.exe" else "adb"
        val home = System.getProperty("user.home")
        val candidates = listOfNotNull(
            readSdkDirFromLocalProperties(project),
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            "$home/Library/Android/sdk",
            "$home/Android/Sdk",
            System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" }
        )
        return candidates.asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, "platform-tools/$adbName") }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    private fun readSdkDirFromLocalProperties(project: Project): String? {
        val file = File(project.basePath ?: return null, "local.properties")
        if (!file.isFile) return null
        return try {
            val props = java.util.Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty("sdk.dir")
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun resolvePathFromObject(result: Any): String? {
        if (result is File) return result.absolutePath
        if (result is String) return result

        // Proactive search for path fields/methods in the result object (handles AdbPath proxy)
        val members =
            listOf("adbPath", "path", "file", "getAdbPath", "getPath", "getFile", "getExecutable")
        for (member in members) {
            try {
                val field = ReflectionCache.getField(result.javaClass, member)
                if (field != null) {
                    val value = field.get(result)
                    extractPath(value)?.let { return it }
                }

                val method = ReflectionCache.getMethod(result.javaClass, member)
                if (method != null && method.parameterCount == 0) {
                    val value = method.invoke(result)
                    extractPath(value)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractPath(value: Any?): String? {
        return when (value) {
            is File -> value.absolutePath
            is String -> value
            is java.nio.file.Path -> value.toAbsolutePath().toString()
            else -> null
        }
    }

    fun getDebugBridge(project: Project): Any? {
        val sdkUtils =
            ReflectionCache.getClass("org.jetbrains.android.sdk.AndroidSdkUtils") ?: return null
        return try {
            val method = ReflectionCache.getMethod(sdkUtils, "getDebugBridge", Project::class.java)
            method?.invoke(null, project)
        } catch (_: Exception) {
            null
        }
    }
}
