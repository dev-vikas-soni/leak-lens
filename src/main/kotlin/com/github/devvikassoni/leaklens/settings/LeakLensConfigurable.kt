package com.github.devvikassoni.leaklens.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.*
import javax.swing.DefaultComboBoxModel

/**
 * Settings page for LeakLens in IDE Preferences.
 * Settings → Tools → LeakLens
 * 
 * Updated to use IntelliJ UI DSL 2 for better alignment with the IDE look and feel.
 * Uses lambda bindings to avoid binary compatibility issues with Kotlin property references.
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
            row {
                checkBox("Analyze immediately on .hprof import")
                    .bindSelected({ settings.analysisOnImport }, { settings.analysisOnImport = it })
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

        group("Memory Monitor") {
            row("Auto heap dump threshold (MB):") {
                intTextField(0..2048)
                    .bindIntText({ settings.autoHeapDumpThresholdMb }, { settings.autoHeapDumpThresholdMb = it })
                comment("0 to disable auto-trigger")
            }
            row("Monitoring interval (ms):") {
                intTextField(1000..60000)
                    .bindIntText({ settings.monitorIntervalMs.toInt() }, { settings.monitorIntervalMs = it.toLong() })
            }
        }

        group("AI-Assisted Analysis (Optional)") {
            row {
                comment("When enabled, anonymized leak traces are sent to an AI API for contextual fix suggestions.")
            }
            row {
                checkBox("Enable AI Suggestions")
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
                checkBox("Anonymize package names before sending to AI")
                    .bindSelected({ settings.aiAnonymizePackageNames }, { settings.aiAnonymizePackageNames = it })
            }
            row {
                comment("⚠️ AI suggestions are clearly marked with a badge. No data is sent without this toggle enabled.")
            }
        }
    }
}
