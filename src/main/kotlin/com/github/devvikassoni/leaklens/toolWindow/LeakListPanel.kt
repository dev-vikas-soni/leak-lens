package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

class LeakListPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<LeakInfo>()
    private val leakList = JBList(listModel)
    var onLeakSelected: ((LeakInfo) -> Unit)? = null

    init {
        leakList.cellRenderer = LeakListCellRenderer()
        leakList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                leakList.selectedValue?.let { onLeakSelected?.invoke(it) }
            }
        }

        add(JBScrollPane(leakList), BorderLayout.CENTER)
        add(createStatusBar(), BorderLayout.SOUTH)
    }

    fun updateLeaks(leaks: List<LeakInfo>) {
        listModel.clear()
        leaks.sortedBy { it.severity.ordinal }.forEach { listModel.addElement(it) }
    }

    private fun createStatusBar(): JPanel {
        val statusPanel = JPanel(BorderLayout())
        val statusLabel = JLabel("Waiting for heap dump...")
        statusPanel.add(statusLabel, BorderLayout.WEST)
        return statusPanel
    }

    private class LeakListCellRenderer : ListCellRenderer<LeakInfo> {
        override fun getListCellRendererComponent(
            list: JList<out LeakInfo>?,
            value: LeakInfo?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel(BorderLayout())
            if (value == null) return panel

            val severityIcon = when (value.severity) {
                LeakSeverity.CRITICAL -> "\uD83D\uDD34"
                LeakSeverity.WARNING -> "\uD83D\uDFE1"
                LeakSeverity.LIBRARY_LEAK -> "\uD83D\uDFE2"
            }

            val label = JLabel("$severityIcon ${value.retainedObjectClassName} - ${value.shortDescription}")
            panel.add(label, BorderLayout.CENTER)

            if (isSelected) {
                panel.background = UIManager.getColor("List.selectionBackground")
                label.foreground = UIManager.getColor("List.selectionForeground")
            }

            return panel
        }
    }
}

