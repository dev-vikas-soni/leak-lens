package com.github.devvikassoni.leaklens.sample.scenarios.singleton

import android.content.Context
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

/**
 * SCENARIO: Context Leak in Singleton
 *
 * Passing an Activity Context to a Singleton which stores it in a field.
 * The Activity cannot be GC'd because the Singleton outlives it.
 */
object AppManager {
    // RED FLAG: Storing an Activity Context in a Singleton
    var context: Context? = null
}

object SingletonScenario : LeakScenario {
    override val id = "singleton-context-leak"
    override val title = "Singleton Context Leak"
    override val description = "Storing Activity context in a global object."
    override val leakSource = "AppManager.context"
    override val expectedInspection = "LeakLensContextPassedToSingleton"
    override val reproductionSteps =
        "1. Pass Activity context to AppManager. 2. Close Activity. 3. Dump heap."
    override val fixSuggestion = "Use applicationContext or a WeakReference."
}
