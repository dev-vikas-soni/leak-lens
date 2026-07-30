package com.github.devvikassoni.leaklens.sample.scenarios.flow

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * SCENARIO: Unsafe Flow Collection
 *
 * Collecting a Flow directly in lifecycleScope.launch instead of repeatOnLifecycle.
 * The collector remains active while the fragment is in the backstack.
 */
class LeakyFlowFragment : Fragment() {
    private val dataFlow = MutableStateFlow("Initial")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RED FLAG: Missing repeatOnLifecycle or flowWithLifecycle
        viewLifecycleOwner.lifecycleScope.launch {
            dataFlow.collect { value ->
                println("Data: $value")
            }
        }
    }
}
