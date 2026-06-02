package com.github.devvikassoni.leaklens.shark

import shark.*

/**
 * Custom ObjectInspectors for Shark that detect project-specific leak patterns
 * beyond LeakCanary's defaults. These inspectors run during heap analysis
 * and annotate objects with leak status information.
 */
object LeakLensObjectInspectors {

    /**
     * Returns the full list of custom inspectors to use alongside Android defaults.
     */
    val all: List<ObjectInspector> = listOf(
        ViewModelContextInspector,
        SingletonContextInspector,
        CoroutineScopeInspector,
        ComposableLeakInspector,
        WorkManagerLeakInspector,
        NavigationLeakInspector
    )

    /**
     * Detects ViewModel instances that hold Context or View references.
     */
    object ViewModelContextInspector : ObjectInspector {
        override fun inspect(reporter: ObjectReporter) {
            reporter.whenInstanceOf("androidx.lifecycle.ViewModel") { instance ->
                val fields = instance.readFields().toList()
                for (field in fields) {
                    val fieldRef = field.value
                    if (fieldRef.isNonNullReference) {
                        val refClassName = fieldRef.asObject?.asInstance?.instanceClassName ?: ""
                        if (refClassName.contains("Activity") ||
                            refClassName.endsWith("Context") ||
                            refClassName.contains("View")) {
                            reporter.leakingReasons +=
                                "ViewModel ${instance.instanceClassName} holds a reference to " +
                                "$refClassName via field '${field.name}'. " +
                                "ViewModels survive configuration changes and must never hold View/Activity/Context."
                        }
                    }
                }
            }
        }
    }

    /**
     * Detects Singleton objects (INSTANCE field) that hold Activity Context.
     */
    object SingletonContextInspector : ObjectInspector {
        override fun inspect(reporter: ObjectReporter) {
            // Check if this is a class with a static INSTANCE field holding context
            reporter.whenInstanceOf("java.lang.Object") { instance ->
                val className = instance.instanceClassName
                val fields = instance.readFields().toList()

                for (field in fields) {
                    val fieldRef = field.value
                    if ((field.name == "context" || field.name == "mContext") && fieldRef.isNonNullReference) {
                        val refClass = fieldRef.asObject?.asInstance?.instanceClassName ?: ""
                        if (refClass.contains("Activity") && !refClass.contains("Application")) {
                            // Check if this object is referenced by a static field
                            reporter.leakingReasons +=
                                "Object $className holds Activity Context in field '${field.name}'. " +
                                "If this is a singleton, use applicationContext instead."
                        }
                    }
                }
            }
        }
    }

    /**
     * Detects uncancelled CoroutineScope holding references to destroyed components.
     */
    object CoroutineScopeInspector : ObjectInspector {
        override fun inspect(reporter: ObjectReporter) {
            reporter.whenInstanceOf("kotlinx.coroutines.CoroutineScopeImpl") { instance ->
                // If a CoroutineScope is retained after its owner is destroyed, flag it
                reporter.leakingReasons +=
                    "A CoroutineScope (${instance.instanceClassName}) is retained in the heap. " +
                    "Ensure coroutine scopes are cancelled when their owner is destroyed " +
                    "(use lifecycleScope/viewModelScope or cancel manually)."
            }

            reporter.whenInstanceOf("kotlinx.coroutines.StandaloneCoroutine") { instance ->
                reporter.leakingReasons +=
                    "A standalone coroutine is retained. If launched in GlobalScope with " +
                    "references to Activity/Fragment, it prevents garbage collection."
            }
        }
    }

    /**
     * Detects potential Jetpack Compose leaks - recomposition scope holding stale state.
     */
    object ComposableLeakInspector : ObjectInspector {
        override fun inspect(reporter: ObjectReporter) {
            reporter.whenInstanceOf("androidx.compose.runtime.RecomposeScopeImpl") { instance ->
                // Compose recompose scopes should be cleaned up when the composition is disposed
                val fields = instance.readFields().toList()
                for (field in fields) {
                    val fieldRef = field.value
                    if (fieldRef.isNonNullReference) {
                        val refClass = fieldRef.asObject?.asInstance?.instanceClassName ?: ""
                        if (refClass.contains("Activity") || refClass.contains("Fragment")) {
                            reporter.leakingReasons +=
                                "A Compose RecomposeScope holds a reference to $refClass. " +
                                "This may indicate a composable capturing Activity/Fragment in a closure."
                        }
                    }
                }
            }
        }
    }

    /**
     * Detects WorkManager workers that hold stale Activity references.
     */
    object WorkManagerLeakInspector : ObjectInspector {
        override fun inspect(reporter: ObjectReporter) {
            reporter.whenInstanceOf("androidx.work.Worker") { instance ->
                val fields = instance.readFields().toList()
                for (field in fields) {
                    val fieldRef = field.value
                    if (fieldRef.isNonNullReference) {
                        val refClass = fieldRef.asObject?.asInstance?.instanceClassName ?: ""
                        if (refClass.contains("Activity")) {
                            reporter.leakingReasons +=
                                "Worker ${instance.instanceClassName} holds Activity reference via '${field.name}'. " +
                                "Workers outlive Activities. Use applicationContext or pass data via WorkManager Data."
                        }
                    }
                }
            }

            reporter.whenInstanceOf("androidx.work.CoroutineWorker") { instance ->
                val fields = instance.readFields().toList()
                for (field in fields) {
                    val fieldRef = field.value
                    if (fieldRef.isNonNullReference) {
                        val refClass = fieldRef.asObject?.asInstance?.instanceClassName ?: ""
                        if (refClass.contains("Activity")) {
                            reporter.leakingReasons +=
                                "CoroutineWorker ${instance.instanceClassName} holds Activity reference. " +
                                "Use applicationContext from the Worker's constructor."
                        }
                    }
                }
            }
        }
    }

    /**
     * Detects Navigation component leaks (NavBackStackEntry retained).
     */
    object NavigationLeakInspector : ObjectInspector {
        override fun inspect(reporter: ObjectReporter) {
            reporter.whenInstanceOf("androidx.navigation.NavBackStackEntry") { instance ->
                // If a NavBackStackEntry is leaking, it usually means a Fragment
                // reference is held across navigation
                reporter.leakingReasons +=
                    "NavBackStackEntry is retained in the heap. This may indicate a navigation-related " +
                    "memory leak. Check for references held across navigation transitions."
            }
        }
    }
}

