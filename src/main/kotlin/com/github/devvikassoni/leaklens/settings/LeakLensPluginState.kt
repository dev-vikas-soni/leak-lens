package com.github.devvikassoni.leaklens.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent application-level state for LeakLens.
 * Tracks global plugin states such as onboarding status.
 */
@Service(Service.Level.APP)
@State(
    name = "LeakLensPluginState",
    storages = [Storage("leaklens_plugin_state.xml")]
)
class LeakLensPluginState : PersistentStateComponent<LeakLensPluginState> {

    var onboardingShown: Boolean = false

    override fun getState(): LeakLensPluginState = this

    override fun loadState(state: LeakLensPluginState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): LeakLensPluginState =
            ApplicationManager.getApplication().getService(LeakLensPluginState::class.java)
    }
}
