package com.github.devvikassoni.leaklens.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.*
import javax.swing.DefaultComboBoxModel

/**
 * Settings page for LeakLens.
 */
class LeakLensConfigurable(private val project: Project) : BoundConfigurable("LeakLens") {

    override fun createPanel() = panel {
        val settings = LeakLensSettingsState.getInstance(project)

        group("General") {
            row {
                checkBox("Enable auto-detect (monitor logcat for LeakCanary dumps)")
                    .bindSelected({ settings.autoDetectEnabled }, { settings.autoDetectEnabled = it })
            }
            row {
                checkBox("Show gutter icons on leak-related classes")
                    .bindSelected({ settings.showGutterIcons }, { settings.showGutterIcons = it })
            }
        }

        group("History") {
            row {
                checkBox("Persist analysis history across sessions")
                    .bindSelected({ settings.persistHistory }, { settings.persistHistory = it })
            }
            row("Max history entries:") {
                intTextField(5..500)
                    .bindIntText({ settings.maxHistoryEntries }, { settings.maxHistoryEntries = it })
            }
        }

        group("Memory Monitor (SDK-Free Capture)") {
            row("Auto-trigger threshold (MB):") {
                intTextField(0..2048)
                    .bindIntText({ settings.autoHeapDumpThresholdMb }, { settings.autoHeapDumpThresholdMb = it })
                comment("Triggers a heap dump when Java heap exceeds this value. Set to 0 to disable.")
            }
        }

        group("AI Fix Suggestions") {
            row {
                text(
                    "Discuss leaks for free using the 'Ask Gemini' button in the tool window, or enable background automation below."
                )
            }
            row {
                checkBox("Enable automatic background analysis")
                    .bindSelected({ settings.aiEnabled }, { settings.aiEnabled = it })
            }
            row("AI Provider:") {
                comboBox(DefaultComboBoxModel(arrayOf("none", "openai", "gemini")))
                    .bindItem({ settings.aiProvider }, { settings.aiProvider = it ?: "none" })
            }
            row("API Key:") {
                passwordField()
                    .bindText({ settings.aiApiKey }, { settings.aiApiKey = it })
                    .align(AlignX.FILL)
            }
            row {
                browserLink("Get a free Gemini API Key from Google AI Studio", "https://aistudio.google.com/app/apikey")
            }
            row {
                checkBox("Anonymize package names before sending to AI")
                    .bindSelected({ settings.aiAnonymizePackageNames }, { settings.aiAnonymizePackageNames = it })
            }
        }
    }
}
