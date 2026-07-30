package com.github.devvikassoni.leaklens.sample.scenarios.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

/**
 * SCENARIO: View Reference Leak in Fragment
 *
 * Storing a View reference in a property and not clearing it in onDestroyView.
 * This is a classic Fragment leak where the View hierarchy is retained even after
 * the Fragment's view is destroyed.
 */
class LeakyFragment : Fragment(), LeakScenario {
    override val id = "fragment-view-leak"
    override val title = "Fragment View Leak"
    override val description = "Retains a reference to a View after onDestroyView()."
    override val leakSource = "leakyView"
    override val expectedInspection = "LeakLensViewReferenceHeld"
    override val reproductionSteps = "1. Navigate to Fragment. 2. Navigate away. 3. Dump heap."
    override val fixSuggestion = "Set view reference to null in onDestroyView()."

    private var leakyView: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RED FLAG: Storing view reference without clearing it later
        leakyView = view.findViewById(android.R.id.text1)
    }

    // Intentionally NOT clearing leakyView in onDestroyView
}
