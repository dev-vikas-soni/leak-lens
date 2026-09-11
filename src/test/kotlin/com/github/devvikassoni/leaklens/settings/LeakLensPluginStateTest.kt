package com.github.devvikassoni.leaklens.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

class LeakLensPluginStateTest : BasePlatformTestCase() {

    fun testPluginStateDefault() {
        val state = LeakLensPluginState.getInstance()
        Assert.assertFalse("Onboarding should not be shown by default", state.onboardingShown)
    }

    fun testPluginStatePersistence() {
        val state = LeakLensPluginState.getInstance()
        state.onboardingShown = true

        val stateAfter = LeakLensPluginState.getInstance()
        Assert.assertTrue("Onboarding state should be persisted", stateAfter.onboardingShown)

        // Reset for other tests if necessary, though BasePlatformTestCase usually handles cleanup or uses fresh components
        state.onboardingShown = false
    }
}
