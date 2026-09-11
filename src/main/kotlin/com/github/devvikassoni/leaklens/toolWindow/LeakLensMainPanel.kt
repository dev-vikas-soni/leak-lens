package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.toUnifiedIssue
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.BorderLayout
import javax.swing.SwingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Main panel for the LeakLens tool window.
 * Contains tabbed interface: Leaks (tree + detail), History.
 *
 * Modernized with JetBrains UI components: JBTabbedPane and OnePixelSplitter.
 */
class LeakLensMainPanel(
    private val project: Project,
    private val leakDetailPanel: LeakDetailPanel
) : JBPanel<LeakLensMainPanel>(BorderLayout()), Disposable {

    private val leakTreePanel = LeakTreePanel(project)
    private val historyPanel = HistoryPanel(project)
    private val memoryPanel = com.github.devvikassoni.leaklens.monitoring.MemoryGraphPanel(project)
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        val tabbedPane = JBTabbedPane(SwingConstants.TOP)

        // Tab 1: Leak Analysis (tree + detail split)
        val leakTab = JBPanel<JBPanel<*>>(BorderLayout())
        val splitPane = OnePixelSplitter(false, 0.35f).apply {
            firstComponent = leakTreePanel
            secondComponent = leakDetailPanel
            setHonorComponentsMinimumSize(true)
        }
        leakTab.add(splitPane, BorderLayout.CENTER)

        // Tab 2: History
        tabbedPane.addTab("Leaks", leakTab)
        tabbedPane.addTab("Memory", memoryPanel)
        tabbedPane.addTab("History", historyPanel)

        // Create Toolbar
        val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        val actionGroup =
            actionManager.getAction("LeakLens.ActionGroup") as com.intellij.openapi.actionSystem.ActionGroup
        val actionToolbar = actionManager.createActionToolbar("LeakLensToolbar", actionGroup, false)
        actionToolbar.targetComponent = this

        add(actionToolbar.component, BorderLayout.WEST)
        add(tabbedPane, BorderLayout.CENTER)

        // Wire selection
        leakTreePanel.onLeakSelected = { issue ->
            if (issue.id.isNotEmpty()) {
                leakDetailPanel.showLeakDetail(issue)
            } else {
                leakDetailPanel.showEmptyState()
            }
        }

        // Tab change listener to refresh history
        tabbedPane.addChangeListener {
            // History is the 3rd tab (index 2)
            if (tabbedPane.selectedIndex == 2) {
                historyPanel.refresh()
            }
        }

        // Subscribe to all leak updates from the service
        val projectService = LeakLensProjectService.getInstance(project)
        combine(
            projectService.leaks,
            projectService.liveIssues,
            projectService.staticIssues
        ) { heapLeaks, liveIssues, staticIssues ->
            val unifiedHeap = (heapLeaks + liveIssues).map { it.toUnifiedIssue() }
            val unifiedStatic = staticIssues.map { it.toUnifiedIssue() }
            unifiedHeap + unifiedStatic
        }.onEach { allIssues ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    refreshLeaks(allIssues)
                }
            }
        }.launchIn(scope)
    }

    /**
     * Called when new leaks are available (from service state).
     */
    fun refreshLeaks(issues: List<com.github.devvikassoni.leaklens.model.UnifiedIssue>) {
        if (project.isDisposed) return
        leakTreePanel.updateLeaks(issues)
    }

    override fun dispose() {
        scope.cancel()
        memoryPanel.dispose()
        leakTreePanel.dispose()
    }
}
