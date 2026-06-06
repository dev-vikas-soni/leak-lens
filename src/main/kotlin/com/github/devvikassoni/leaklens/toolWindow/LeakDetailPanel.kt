package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.ai.AiUtils
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.SourceNavigationService
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextPane
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

    private val askGeminiButton = JButton("Ask Gemini AI").apply {
        icon = AllIcons.Actions.QuickfixBulb
        toolTipText = "Copy prompt for Android Studio Gemini Assistant"
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

    init {
        border = JBUI.Borders.empty(8)
        
        val header = panel {
            row {
                cell(severityLabel)
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

        mainContent.add(splitPane, "CONTENT")
        mainContent.add(emptyStatePanel, "EMPTY")

        add(header, BorderLayout.NORTH)
        add(mainContent, BorderLayout.CENTER)
        showEmptyState()
    }

    fun showLeakDetail(leak: LeakInfo) {
        severityLabel.icon = when (leak.severity) {
            LeakSeverity.CRITICAL -> AllIcons.General.Error
            LeakSeverity.WARNING -> AllIcons.General.Warning
            LeakSeverity.LIBRARY_LEAK -> AllIcons.General.Information
        }
        severityLabel.text = leak.severity.displayName
        classLabel.text = "Class: ${leak.retainedObjectClassName}"

        val sizeStr = if (leak.retainedByteSize >= 1024 * 1024) {
            "${leak.retainedByteSize / (1024 * 1024)} MB"
        } else {
            "${leak.retainedByteSize / 1024} KB"
        }
        sizeLabel.text = "Retained: $sizeStr | Objects: ${leak.retainedObjectCount}"

        // Configure Gemini button
        askGeminiButton.isVisible = true
        for (al in askGeminiButton.actionListeners) askGeminiButton.removeActionListener(al)
        askGeminiButton.addActionListener {
            val prompt = AiUtils.askGemini(project, leak)
            fixSuggestionArea.text = "PROMPT COPIED TO CLIPBOARD:\n\n$prompt"
        }

        buildClickableTrace(leak)

        val fix = leak.suggestedFix
        if (fix == null || fix.contains("No fix suggestion available")) {
            fixSuggestionArea.text =
                "No automatic fix found for this pattern.\n\nUse the 'Ask Gemini AI' button above to get assistance from Android Studio's built-in AI."
        } else {
            fixSuggestionArea.text = fix
        }

        (mainContent.layout as CardLayout).show(mainContent, "CONTENT")
    }

    fun showEmptyState() {
        severityLabel.text = ""
        severityLabel.icon = null
        classLabel.text = ""
        sizeLabel.text = ""
        tracePane.text = ""
        fixSuggestionArea.text = ""
        askGeminiButton.isVisible = false

        (mainContent.layout as CardLayout).show(mainContent, "EMPTY")
    }

    private fun buildClickableTrace(leak: LeakInfo) {
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

        doc.insertString(doc.length, "═══ REFERENCE CHAIN ═══\n\n", normalStyle)
        for ((index, ref) in leak.referenceChain.withIndex()) {
            doc.insertString(doc.length, "  ".repeat(index) + "↓ ", normalStyle)
            doc.insertString(doc.length, ref.owningClassName, linkStyle)
            doc.insertString(doc.length, ".${ref.referenceName} (${ref.referenceType})\n", normalStyle)
        }
        
        doc.insertString(doc.length, "\n═══ FULL TRACE ═══\n\n", normalStyle)
        doc.insertString(doc.length, leak.leakTrace, normalStyle)
    }

    private fun handleTraceClick(e: MouseEvent) {
        val offset = tracePane.viewToModel2D(e.point)
        val doc = tracePane.styledDocument
        val text = doc.getText(0, doc.length)
        val className = extractClassNameAtOffset(text, offset)
        if (className != null) {
            SourceNavigationService.getInstance(project).navigateToClass(className)
        }
    }

    private fun extractClassNameAtOffset(text: String, offset: Int): String? {
        if (offset < 0 || offset >= text.length) return null
        var start = offset; var end = offset
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '.' || text[start - 1] == '$')) start--
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '$')) end++
        val word = text.substring(start, end)
        return if (word.contains('.') && word.first().isLetter()) word else null
    }
}
