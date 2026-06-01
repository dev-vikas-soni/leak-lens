package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTabbedPane

/**
 * Main panel for the LeakLens tool window.
 * Contains tabbed interface: Leaks (tree + detail), History.
 */
class LeakLensMainPanel(
    private val project: Project,
    private val leakListPanel: LeakListPanel,
    private val leakDetailPanel: LeakDetailPanel
) : JPanel(BorderLayout()) {

    private val leakTreePanel = LeakTreePanel(project)
    private val historyPanel = HistoryPanel(project)

    init {
        val tabbedPane = JTabbedPane()

        // Tab 1: Leak Analysis (tree + detail split)
        val leakTab = JPanel(BorderLayout())
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leakTreePanel, leakDetailPanel).apply {
            resizeWeight = 0.35
            dividerLocation = 320
        }
        leakTab.add(splitPane, BorderLayout.CENTER)

        // Tab 2: History
        tabbedPane.addTab("Leaks", leakTab)
        tabbedPane.addTab("History", historyPanel)

        add(tabbedPane, BorderLayout.CENTER)

        // Wire selection
        leakTreePanel.onLeakSelected = { leak ->
            if (leak.signature.isNotEmpty()) {
                leakDetailPanel.showLeakDetail(leak)
            } else {
                leakDetailPanel.showEmptyState()
            }
        }

        // Also keep old list panel wired (for backwards compat)
        leakListPanel.onLeakSelected = { leak ->
            leakDetailPanel.showLeakDetail(leak)
        }

        // Tab change listener to refresh history
        tabbedPane.addChangeListener {
            if (tabbedPane.selectedIndex == 1) {
                historyPanel.refresh()
            }
        }
    }

    /**
     * Called when new leaks are available (from service state).
     */
    fun refreshLeaks(leaks: List<LeakInfo>) {
        leakTreePanel.updateLeaks(leaks)
        leakListPanel.updateLeaks(leaks)
    }
}
