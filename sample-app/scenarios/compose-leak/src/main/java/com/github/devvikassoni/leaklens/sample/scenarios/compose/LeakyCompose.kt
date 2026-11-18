package com.github.devvikassoni.leaklens.sample.scenarios.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

class LeakyComposeViewModel : ViewModel() {
    private var context: Context? = null

    fun setContext(ctx: Context) {
        this.context = ctx
    }
}

/**
 * SCENARIO: Compose Context Capture
 *
 * Passing LocalContext.current (which is an Activity) into a ViewModel or state holder
 * inside Compose code.
 */
@Composable
fun LeakyComposeScreen(viewModel: LeakyComposeViewModel = LeakyComposeViewModel()) {
    val context = LocalContext.current

    // RED FLAG: Passing Activity Context to ViewModel from Compose
    remember {
        viewModel.setContext(context)
        "Leaking: $context"
    }
}

object ComposeScenario : LeakScenario {
    override val id = "compose-context"
    override val title = "Compose Context Leak"
    override val description = "Passing Activity Context to ViewModel from Compose."
    override val leakSource = "LocalContext.current"
    override val expectedInspection = "LeakLensComposeContextLeak"
    override val reproductionSteps =
        "1. Navigate to Compose screen. 2. Rotate device. 3. Dump heap."
    override val fixSuggestion = "Use applicationContext or keep Context usage in UI layer."
}
