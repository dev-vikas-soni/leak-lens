package com.github.devvikassoni.leaklens.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.*
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

/**
 * Settings page for LeakLens in IDE Preferences.
 * Settings → Tools → LeakLens
 */
class LeakLensConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private var autoDetectCheckbox: JCheckBox? = null
    private var gutterIconsCheckbox: JCheckBox? = null
    private var aiEnabledCheckbox: JCheckBox? = null
    private var aiProviderCombo: JComboBox<String>? = null
    private var aiApiKeyField: JPasswordField? = null
    private var aiAnonymizeCheckbox: JCheckBox? = null
    private var maxHistorySpinner: JSpinner? = null
    private var persistHistoryCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "LeakLens"

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())
        val settingsPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 8, 4, 8)
            gridx = 0
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
        }

        val settings = LeakLensSettingsState.getInstance(project)

        // === General Section ===
        settingsPanel.add(JLabel("<html><b>General</b></html>"), gbc)
        gbc.gridy++

        autoDetectCheckbox = JCheckBox("Enable auto-detect (monitor logcat for LeakCanary dumps)", settings.autoDetectEnabled)
        settingsPanel.add(autoDetectCheckbox, gbc)
        gbc.gridy++

        gutterIconsCheckbox = JCheckBox("Show gutter icons on leak-related classes", settings.showGutterIcons)
        settingsPanel.add(gutterIconsCheckbox, gbc)
        gbc.gridy++

        // === History Section ===
        settingsPanel.add(JLabel("<html><br/><b>History</b></html>"), gbc)
        gbc.gridy++

        persistHistoryCheckbox = JCheckBox("Persist analysis history across sessions", settings.persistHistory)
        settingsPanel.add(persistHistoryCheckbox, gbc)
        gbc.gridy++

        val historyPanel = JPanel().apply {
            add(JLabel("Max history entries:"))
            maxHistorySpinner = JSpinner(SpinnerNumberModel(settings.maxHistoryEntries, 5, 500, 5))
            add(maxHistorySpinner)
        }
        settingsPanel.add(historyPanel, gbc)
        gbc.gridy++

        // === AI Section ===
        settingsPanel.add(JLabel("<html><br/><b>AI-Assisted Analysis (Optional)</b></html>"), gbc)
        gbc.gridy++

        settingsPanel.add(JLabel("<html><small>When enabled, anonymized leak traces are sent to an AI API for contextual fix suggestions.</small></html>"), gbc)
        gbc.gridy++

        aiEnabledCheckbox = JCheckBox("Enable AI Suggestions", settings.aiEnabled)
        settingsPanel.add(aiEnabledCheckbox, gbc)
        gbc.gridy++

        val providerPanel = JPanel().apply {
            add(JLabel("AI Provider:"))
            aiProviderCombo = JComboBox(arrayOf("none", "openai", "gemini"))
            aiProviderCombo?.selectedItem = settings.aiProvider
            add(aiProviderCombo)
        }
        settingsPanel.add(providerPanel, gbc)
        gbc.gridy++

        val apiKeyPanel = JPanel().apply {
            add(JLabel("API Key:"))
            aiApiKeyField = JPasswordField(30)
            aiApiKeyField?.text = settings.aiApiKey
            add(aiApiKeyField)
        }
        settingsPanel.add(apiKeyPanel, gbc)
        gbc.gridy++

        aiAnonymizeCheckbox = JCheckBox("Anonymize package names before sending to AI", settings.aiAnonymizePackageNames)
        settingsPanel.add(aiAnonymizeCheckbox, gbc)
        gbc.gridy++

        settingsPanel.add(JLabel("<html><small>⚠️ AI suggestions are clearly marked with a badge. No data is sent without this toggle enabled.</small></html>"), gbc)

        panel.add(settingsPanel, BorderLayout.NORTH)
        mainPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = LeakLensSettingsState.getInstance(project)
        return autoDetectCheckbox?.isSelected != settings.autoDetectEnabled ||
               gutterIconsCheckbox?.isSelected != settings.showGutterIcons ||
               aiEnabledCheckbox?.isSelected != settings.aiEnabled ||
               aiProviderCombo?.selectedItem != settings.aiProvider ||
               String(aiApiKeyField?.password ?: charArrayOf()) != settings.aiApiKey ||
               aiAnonymizeCheckbox?.isSelected != settings.aiAnonymizePackageNames ||
               (maxHistorySpinner?.value as? Int) != settings.maxHistoryEntries ||
               persistHistoryCheckbox?.isSelected != settings.persistHistory
    }

    override fun apply() {
        val settings = LeakLensSettingsState.getInstance(project)
        settings.autoDetectEnabled = autoDetectCheckbox?.isSelected ?: false
        settings.showGutterIcons = gutterIconsCheckbox?.isSelected ?: true
        settings.aiEnabled = aiEnabledCheckbox?.isSelected ?: false
        settings.aiProvider = aiProviderCombo?.selectedItem as? String ?: "none"
        settings.aiApiKey = String(aiApiKeyField?.password ?: charArrayOf())
        settings.aiAnonymizePackageNames = aiAnonymizeCheckbox?.isSelected ?: true
        settings.maxHistoryEntries = (maxHistorySpinner?.value as? Int) ?: 50
        settings.persistHistory = persistHistoryCheckbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = LeakLensSettingsState.getInstance(project)
        autoDetectCheckbox?.isSelected = settings.autoDetectEnabled
        gutterIconsCheckbox?.isSelected = settings.showGutterIcons
        aiEnabledCheckbox?.isSelected = settings.aiEnabled
        aiProviderCombo?.selectedItem = settings.aiProvider
        aiApiKeyField?.text = settings.aiApiKey
        aiAnonymizeCheckbox?.isSelected = settings.aiAnonymizePackageNames
        maxHistorySpinner?.value = settings.maxHistoryEntries
        persistHistoryCheckbox?.isSelected = settings.persistHistory
    }
}

