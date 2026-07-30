package com.github.devvikassoni.leaklens.sample.scenarios.workmanager

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

/**
 * SCENARIO: Context Leak in WorkManager
 *
 * Storing a Context in a field instead of using applicationContext.
 * If the worker outlives the Activity, it retains a reference to it.
 */
class LeakyWorker(context: Context, params: WorkerParameters) : Worker(context, params),
    LeakScenario {
    override val id = "workmanager-leak"
    override val title = "WorkManager Context Leak"
    override val description = "Storing Activity context in a Worker field."
    override val leakSource = "leakyContext"
    override val expectedInspection = "LeakLensWorkerContextLeak"
    override val reproductionSteps =
        "1. Start Work with Activity context. 2. Close Activity. 3. Dump heap."
    override val fixSuggestion = "Use applicationContext or get it from context property."

    // RED FLAG: Storing context in a field
    private val leakyContext = context

    override fun doWork(): Result {
        println("Work: $leakyContext")
        return Result.success()
    }
}
