package com.github.devvikassoni.leaklens.compat

/**
 * Facade for IDevice and Client interactions across different ddmlib versions.
 */
object DeviceFacade {

    fun getProcessName(clientData: Any): String? {
        val clazz = clientData.javaClass
        return listOf("getProcessName", "getClientDescription").firstNotNullOfOrNull { methodName ->
            ReflectionCache.getMethod(clazz, methodName)?.invoke(clientData) as? String
        }
    }

    fun isOnline(device: Any): Boolean {
        return try {
            val method = ReflectionCache.getMethod(device.javaClass, "isOnline")
            method?.invoke(device) as? Boolean == true
        } catch (_: Exception) {
            false
        }
    }

    fun getSerialNumber(device: Any): String? {
        return try {
            val method = ReflectionCache.getMethod(device.javaClass, "getSerialNumber")
            method?.invoke(device) as? String
        } catch (_: Exception) {
            null
        }
    }
}
