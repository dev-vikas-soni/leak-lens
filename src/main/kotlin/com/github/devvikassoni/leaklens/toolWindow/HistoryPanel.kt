package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.AnalysisHistoryEntry
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * Panel showing analysis history/timeline.
 */
class HistoryPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<AnalysisHistoryEntry>()
    private val historyList = JBList(listModel)

    var onHistoryEntrySelected: ((AnalysisHistoryEntry) -> Unit)? = null

    init {
        historyList.cellRenderer = HistoryCellRenderer()
        historyList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                historyList.selectedValue?.let { onHistoryEntrySelected?.invoke(it) }
            }
        }

        add(JBScrollPane(historyList), BorderLayout.CENTER)
        add(JLabel("  Analysis History"), BorderLayout.NORTH)
    }

    fun refresh() {
        val history = LeakLensProjectService.getInstance(project).history.value
        listModel.clear()
        history.sortedByDescending { it.timestamp }.forEach { listModel.addElement(it) }
    }

    private class HistoryCellRenderer : ListCellRenderer<AnalysisHistoryEntry> {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        override fun getListCellRendererComponent(
            list: JList<out AnalysisHistoryEntry>?,
            value: AnalysisHistoryEntry?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val panel = JPanel(BorderLayout())
            if (value == null) return panel

            val timeStr = dateFormat.format(Date(value.timestamp))
            val summary = "🔴${value.criticalCount} 🟡${value.warningCount} 🟢${value.libraryLeakCount}"
            val label = JLabel(
                "<html><b>$timeStr</b> — ${value.leakCount} leaks ($summary)<br/><small>${value.sourceName}</small></html>"
            )

            panel.add(label, BorderLayout.CENTER)
            panel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

            if (isSelected) {
                panel.background = UIManager.getColor("List.selectionBackground")
                label.foreground = UIManager.getColor("List.selectionForeground")
            }

            return panel
        }
    }
}
