package com.github.devvikassoni.leaklens.sample.scenarios.activity

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

object LeakySingleton {
    val listeners = mutableListOf<(String) -> Unit>()
}

class LeakyActivity : Activity(), LeakScenario {
    override val id = "activity-callback"
    override val title = "Anonymous Callback Leak"
    override val description =
        "A static singleton retains a reference to this activity via a callback."
    override val leakSource = "LeakySingleton.listeners"
    override val expectedInspection = "LeakLensStaticActivityReference"
    override val reproductionSteps = "1. Open screen. 2. Press Back. 3. Trigger Heap Dump."
    override val fixSuggestion = "Unregister listener in onDestroy() or use WeakReference."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "Leaky Activity (Back to leak)"
            textSize = 24f
        }
        setContentView(tv)

        // RED FLAG: Anonymous callback captures 'this'
        LeakySingleton.listeners.add { message ->
            println("Received: $message from ${this@LeakyActivity}")
        }
    }
}
