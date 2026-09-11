package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.ai.AiUtils
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.UnifiedIssue
import com.github.devvikassoni.leaklens.model.toUnifiedIssue
import com.github.devvikassoni.leaklens.services.SourceNavigationService
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextPane
import javax.swing.Timer
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Right panel showing leak trace details and smart fix suggestions.
 */
class LeakDetailPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tracePane = JTextPane().apply {
        isEditable = false
        contentType = "text/plain"
        font = Font("JetBrains Mono", Font.PLAIN, 12)
    }

    private val fixSuggestionArea = JTextArea().apply {
        isEditable = false
        font = Font("JetBrains Mono", Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }

    private val severityLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 14f) }
    private val classLabel = JBLabel().apply { font = font.deriveFont(Font.PLAIN, 12f) }
    private val sizeLabel = JBLabel().apply { font = font.deriveFont(Font.PLAIN, 11f) }

    private var currentIssue: UnifiedIssue? = null

    private val askGeminiButton = JButton("Ask Gemini AI").apply {
        icon = AllIcons.Actions.QuickfixBulb
        toolTipText = "Copy prompt for Android Studio Gemini Assistant"
        isVisible = false
    }

    private val verifyFixButton = JButton("Verify Fix").apply {
        icon = AllIcons.Actions.Execute
        toolTipText = "Trigger a new heap dump to verify if the fix resolved the leak"
        isVisible = false
    }

    private val linkMappingButton = JButton("Link mapping.txt").apply {
        icon = AllIcons.Actions.ListFiles
        toolTipText = "Trace appears obfuscated. Select a ProGuard/R8 mapping file to deobfuscate."
        isVisible = false
    }

    private val emptyStatePanel = panel {
        row {
            icon(AllIcons.General.Information).align(AlignX.CENTER)
        }
        row {
            label("Select a leak from the left panel to see details").bold().align(AlignX.CENTER)
        }
        row {
            text("No leaks yet? Try these steps:").align(AlignX.CENTER)
        }
        indent {
            row {
                text("1. Connect a debuggable Android device or emulator")
            }
            row {
                text("2. Click the 'Dump Heap' icon in the toolbar")
            }
            row {
                text("3. Analyze the generated trace and get AI fix suggestions")
            }
        }
    }.apply {
        border = JBUI.Borders.empty(20)
    }

    private val mainContent = JPanel(CardLayout())

    // --- Animation state ---
    private var animationAlpha = 1.0f
    private var fadeTimer: Timer? = null

    /** Highlights the panel momentarily with a fade-in from transparent to opaque. */
    private fun fadeIn() {
        fadeTimer?.stop()
        animationAlpha = 0f
        fadeTimer = Timer(16) { _ ->
            animationAlpha = (animationAlpha + 0.08f).coerceAtMost(1f)
            mainContent.repaint()
            if (animationAlpha >= 1f) fadeTimer?.stop()
        }
        fadeTimer?.start()
    }

    init {
        border = JBUI.Borders.empty(8)

        val header = panel {
            row {
                cell(severityLabel)
                cell(linkMappingButton).align(AlignX.RIGHT)
                cell(verifyFixButton).align(AlignX.RIGHT)
                cell(askGeminiButton).align(AlignX.RIGHT)
            }
            row {
                cell(classLabel).align(AlignX.FILL)
            }
            row {
                cell(sizeLabel).align(AlignX.FILL)
            }
        }

        tracePane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                handleTraceClick(e)
            }
        })
        tracePane.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = JBScrollPane(tracePane).apply {
                border = BorderFactory.createTitledBorder("Leak Trace (click class names to navigate)")
            }
            bottomComponent = JBScrollPane(fixSuggestionArea).apply {
                border = BorderFactory.createTitledBorder("Suggested Fix")
            }
            resizeWeight = 0.6
        }

        val animatedContent = object : JPanel(BorderLayout()) {
            override fun paintChildren(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, animationAlpha)
                super.paintChildren(g2)
            }
        }.apply {
            isOpaque = false
            add(splitPane, BorderLayout.CENTER)
        }

        mainContent.add(animatedContent, "CONTENT")
        mainContent.add(emptyStatePanel, "EMPTY")

        add(header, BorderLayout.NORTH)
        add(mainContent, BorderLayout.CENTER)
        showEmptyState()
    }

    fun showLeakDetail(issue: UnifiedIssue) {
        currentIssue = issue
        severityLabel.icon = when (issue.severity) {
            LeakSeverity.CRITICAL -> AllIcons.General.Error
            LeakSeverity.WARNING -> AllIcons.General.Warning
            LeakSeverity.LIBRARY_LEAK -> AllIcons.General.Information
        }
        severityLabel.text = issue.severity.displayName

        classLabel.text = "Class: ${issue.className}"
        classLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        for (al in classLabel.mouseListeners) classLabel.removeMouseListener(al)
        classLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                SourceNavigationService.getInstance(project)
                    .navigateToClass(issue.className)
            }
        })
        classLabel.toolTipText = "Click to open ${issue.className}"

        if (issue.source == com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS) {
            val leak = issue.originalIssue as LeakInfo
            val sizeStr = if (leak.retainedByteSize >= 1024 * 1024) {
                "${leak.retainedByteSize / (1024 * 1024)} MB"
            } else {
                "${leak.retainedByteSize / 1024} KB"
            }
            sizeLabel.text = "Retained: $sizeStr | Objects: ${leak.retainedObjectCount}"
        } else {
            sizeLabel.text = "Confidence: ${issue.confidence} | Source: Static Analysis"
        }

        // Configure Gemini button
        askGeminiButton.isVisible = true
        for (al in askGeminiButton.actionListeners) askGeminiButton.removeActionListener(al)
        askGeminiButton.addActionListener {
            val leakInfo = when (issue.source) {
                com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS -> issue.originalIssue as LeakInfo
                com.github.devvikassoni.leaklens.model.IssueSource.STATIC_ANALYSIS -> {
                    val static =
                        issue.originalIssue as com.github.devvikassoni.leaklens.model.StaticAnalysisIssue
                    LeakInfo(
                        signature = static.fingerprint,
                        shortDescription = static.description,
                        leakTrace = static.description,
                        retainedObjectClassName = static.className ?: "Unknown",
                        retainedByteSize = 0,
                        retainedObjectCount = 1,
                        severity = issue.severity,
                        referenceChain = emptyList(),
                        suggestedFix = static.suggestedFix
                    )
                }

                else -> null
            }
            leakInfo?.let { AiUtils.askGemini(project, it) }
            fixSuggestionArea.text =
                "PROMPT COPIED TO CLIPBOARD:\n\nAnalyze this leak using Gemini Assistant."
        }

        // Configure Verify Fix button
        val coordinator =
            com.github.devvikassoni.leaklens.services.LeakAnalysisCoordinator.getInstance(project)
        verifyFixButton.isVisible =
            coordinator.lastDumpContext != null && issue.source == com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS
        for (al in verifyFixButton.actionListeners) verifyFixButton.removeActionListener(al)
        verifyFixButton.addActionListener {
            val context = coordinator.lastDumpContext
            if (context != null) {
                coordinator.triggerAndAnalyze(context.deviceSerial, context.packageName)
            }
        }

        // Configure Mapping button
        val deobService =
            com.github.devvikassoni.leaklens.deobfuscation.DeobfuscationService.getInstance(project)
        linkMappingButton.isVisible =
            !deobService.hasMappings() && issue.source == com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS
        for (al in linkMappingButton.actionListeners) linkMappingButton.removeActionListener(al)
        linkMappingButton.addActionListener {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select ProGuard/R8 Mapping File")
                .withDescription("Choose the mapping.txt file for the current build to deobfuscate the leak trace.")

            val file = FileChooser.chooseFile(descriptor, project, null)
            if (file != null) {
                if (deobService.loadMappingFile(java.io.File(file.path))) {
                    val settings =
                        com.github.devvikassoni.leaklens.settings.LeakLensSettingsState.getInstance(
                            project
                        )
                    settings.mappingFilePath = file.path

                    if (issue.source == com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS) {
                        val leak = issue.originalIssue as LeakInfo
                        val deobLeak = leak.copy(
                            retainedObjectClassName = deobService.deobfuscateClassName(leak.retainedObjectClassName),
                            leakTrace = deobService.deobfuscateTrace(leak.leakTrace),
                            referenceChain = leak.referenceChain.map { ref ->
                                ref.copy(owningClassName = deobService.deobfuscateClassName(ref.owningClassName))
                            }
                        )
                        showLeakDetail(deobLeak.toUnifiedIssue())
                    }
                }
            }
        }

        buildClickableTrace(issue)

        val fix = issue.suggestedFix
        if (fix == null || fix.contains("No fix suggestion available")) {
            fixSuggestionArea.text =
                "No automatic fix found for this pattern.\n\nUse the 'Ask Gemini AI' button above to get assistance from Android Studio's built-in AI."
        } else {
            fixSuggestionArea.text = fix
        }

        (mainContent.layout as CardLayout).show(mainContent, "CONTENT")
        fadeIn()
    }

    fun showEmptyState() {
        currentIssue = null
        severityLabel.text = ""
        severityLabel.icon = null
        classLabel.text = ""
        sizeLabel.text = ""
        tracePane.text = ""
        fixSuggestionArea.text = ""
        askGeminiButton.isVisible = false
        verifyFixButton.isVisible = false
        linkMappingButton.isVisible = false

        (mainContent.layout as CardLayout).show(mainContent, "EMPTY")
    }

    private fun buildClickableTrace(issue: UnifiedIssue) {
        val doc = tracePane.styledDocument
        doc.remove(0, doc.length)

        val normalStyle = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, "JetBrains Mono")
            StyleConstants.setFontSize(this, 12)
        }
        val linkStyle = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, "JetBrains Mono")
            StyleConstants.setFontSize(this, 12)
            StyleConstants.setForeground(this, Color(0x58, 0x9D, 0xF6))
            StyleConstants.setUnderline(this, true)
            StyleConstants.setBold(this, true)
        }

        if (issue.source == com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS) {
            val leak = issue.originalIssue as LeakInfo
            doc.insertString(doc.length, "═══ REFERENCE CHAIN ═══\n\n", normalStyle)
            for ((index, ref) in leak.referenceChain.withIndex()) {
                doc.insertString(doc.length, "  ".repeat(index) + "↓ ", normalStyle)
                doc.insertString(doc.length, ref.owningClassName, linkStyle)
                doc.insertString(doc.length, ".${ref.referenceName}", linkStyle)
                doc.insertString(doc.length, " (${ref.referenceType})\n", normalStyle)
            }
            doc.insertString(doc.length, "\n═══ FULL TRACE ═══\n\n", normalStyle)
            doc.insertString(doc.length, leak.leakTrace, normalStyle)
        } else {
            doc.insertString(doc.length, "═══ STATIC ANALYSIS FINDING ═══\n\n", normalStyle)
            doc.insertString(doc.length, issue.description, normalStyle)
            doc.insertString(
                doc.length,
                "\n\nLocation: ${issue.filePath}:${issue.line}",
                normalStyle
            )
        }
    }

    private fun handleTraceClick(e: MouseEvent) {
        val offset = tracePane.viewToModel2D(e.point)
        val doc = tracePane.styledDocument
        val text = doc.getText(0, doc.length)

        var start = offset
        var end = offset
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '.' || text[start - 1] == '$' || text[start - 1] == '_')) start--
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '$' || text[end] == '_')) end++
        val word = text.substring(start, end)

        if (word.isBlank()) return

        if (currentIssue?.source == com.github.devvikassoni.leaklens.model.IssueSource.HEAP_ANALYSIS) {
            val leak = currentIssue?.originalIssue as LeakInfo
            leak.referenceChain.forEach { ref ->
                if (word.endsWith(ref.referenceName) || word == ref.owningClassName) {
                    SourceNavigationService.getInstance(project).navigateToReference(ref)
                    return
                }
            }
        }

        val className = if (word.contains('.') && word.first().isLetter()) word else null
        if (className != null) {
            SourceNavigationService.getInstance(project).navigateToClass(className)
        }
    }
}
