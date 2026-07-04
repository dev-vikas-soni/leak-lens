package com.github.devvikassoni.leaklens.compat

import com.intellij.openapi.diagnostic.thisLogger
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance cache for reflected members.
 * Prevents the overhead of Class.forName and getMethod during hot-path execution.
 */
object ReflectionCache {
    private val logger = thisLogger()
    private val classCache = ConcurrentHashMap<String, Class<*>>()
    private val methodCache = ConcurrentHashMap<String, Method>()
    private val fieldCache = ConcurrentHashMap<String, Field>()

    fun getClass(className: String): Class<*>? {
        return classCache.getOrPut(className) {
            try {
                Class.forName(className)
            } catch (e: Exception) {
                logger.debug("Class $className not found in current IDE version")
                Void.TYPE // Marker for "Not Found"
            }
        }.takeIf { it != Void.TYPE }
    }

    fun getMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        val key = "${clazz.name}.$methodName(${parameterTypes.joinToString { it.simpleName }})"
        return methodCache.getOrPut(key) {
            try {
                clazz.getMethod(methodName, *parameterTypes).also { it.isAccessible = true }
            } catch (e: Exception) {
                try {
                    clazz.getDeclaredMethod(methodName, *parameterTypes)
                        .also { it.isAccessible = true }
                } catch (e2: Exception) {
                    logger.debug("Method $key not found in ${clazz.name}")
                    DummyMethodHolder.DUMMY_METHOD
                }
            }
        }.takeIf { it != DummyMethodHolder.DUMMY_METHOD }
    }

    fun getField(clazz: Class<*>, fieldName: String): Field? {
        val key = "${clazz.name}.$fieldName"
        return fieldCache.getOrPut(key) {
            try {
                clazz.getField(fieldName).also { it.isAccessible = true }
            } catch (e: Exception) {
                try {
                    clazz.getDeclaredField(fieldName).also { it.isAccessible = true }
                } catch (e2: Exception) {
                    logger.debug("Field $fieldName not found in ${clazz.name}")
                    DummyFieldHolder.DUMMY_FIELD
                }
            }
        }.takeIf { it != DummyFieldHolder.DUMMY_FIELD }
    }

    private object DummyMethodHolder {
        val DUMMY_METHOD: Method = Any::class.java.getMethod("toString")
    }

    private object DummyFieldHolder {
        // We use a private field as a marker
        val DUMMY_FIELD: Field = ReflectionCache::class.java.getDeclaredFields().first()
    }
}
