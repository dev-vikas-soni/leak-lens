package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.actions.AskGeminiAction
import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.model.LeakTraceReference
import com.github.devvikassoni.leaklens.services.SourceNavigationService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
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
    private val actionPanel = JPanel(BorderLayout())

    init {
        border = JBUI.Borders.empty(8)
        
        val header = panel {
            row {
                cell(severityLabel)
                cell(actionPanel).align(AlignX.RIGHT)
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

        add(header, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
        showEmptyState()
    }

    fun showLeakDetail(leak: LeakInfo) {
        val severityEmoji = when (leak.severity) {
            LeakSeverity.CRITICAL -> "🔴"
            LeakSeverity.WARNING -> "🟡"
            LeakSeverity.LIBRARY_LEAK -> "🟢"
        }
        severityLabel.text = "$severityEmoji ${leak.severity.displayName}"
        classLabel.text = "Class: ${leak.retainedObjectClassName}"

        val sizeStr = if (leak.retainedByteSize >= 1024 * 1024) {
            "${leak.retainedByteSize / (1024 * 1024)} MB"
        } else {
            "${leak.retainedByteSize / 1024} KB"
        }
        sizeLabel.text = "Retained: $sizeStr | Objects: ${leak.retainedObjectCount}"

        // Update actions
        updateActions(leak)

        buildClickableTrace(leak)
        fixSuggestionArea.text = leak.suggestedFix ?: "No fix suggestion available."
    }

    private fun updateActions(leak: LeakInfo) {
        actionPanel.removeAll()
        val group = DefaultActionGroup().apply {
            add(AskGeminiAction(leak))
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        toolbar.targetComponent = actionPanel
        actionPanel.add(toolbar.component)
        actionPanel.revalidate()
    }

    fun showEmptyState() {
        severityLabel.text = ""
        classLabel.text = ""
        sizeLabel.text = ""
        tracePane.text = "Select an item to view details."
        fixSuggestionArea.text = ""
        actionPanel.removeAll()
    }

    private fun buildClickableTrace(leak: LeakInfo) {
        val doc = tracePane.styledDocument
        doc.remove(0, doc.length)

        val normalStyle = SimpleAttributeSet().apply { StyleConstants.setFontFamily(this, "JetBrains Mono"); StyleConstants.setFontSize(this, 12) }
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
