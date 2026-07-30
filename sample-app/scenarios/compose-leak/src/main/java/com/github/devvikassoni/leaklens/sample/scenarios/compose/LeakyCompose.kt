package com.github.devvikassoni.leaklens.sample.scenarios.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

/**
 * SCENARIO: Compose Context Capture
 *
 * Passing LocalContext.current (which is an Activity) into a long-lived
 * 'remember' block or state holder.
 */
@Composable
fun LeakyComposeScreen() {
    val context = LocalContext.current

    // RED FLAG: Capture context in a block that might outlive the Activity
    // if passed to a global state or ViewModel incorrectly.
    val leak = remember {
        // In a real app, this might be passed to a ViewModel
        "Leaking: $context"
    }
}

object ComposeScenario : LeakScenario {
    override val id = "compose-context"
    override val title = "Compose Context Leak"
    override val description = "remember block capturing Activity Context incorrectly."
    override val leakSource = "LocalContext.current"
    override val expectedInspection = "LeakLensComposeContextLeak"
    override val reproductionSteps =
        "1. Navigate to Compose screen. 2. Rotate device. 3. Dump heap."
    override val fixSuggestion = "Use applicationContext or rememberUpdatedState."
}
