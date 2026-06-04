package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.monitoring.MemoryGraphPanel
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class LeakLensToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val leakListPanel = LeakListPanel(project)
        val leakDetailPanel = LeakDetailPanel(project)
        val mainPanel = LeakLensMainPanel(project, leakListPanel, leakDetailPanel)

        // Tab 1: Leak Analysis
        val leakContent = ContentFactory.getInstance().createContent(mainPanel, "Leaks", false)
        toolWindow.contentManager.addContent(leakContent)

        // Tab 2: Memory Monitor (Phase 6)
        val memoryPanel = MemoryGraphPanel(project)
        val memoryContent = ContentFactory.getInstance().createContent(memoryPanel, "Memory", false)
        toolWindow.contentManager.addContent(memoryContent)

        // Subscribe to leak updates from the service
        // Avoid Dispatchers.Main as it throws without kotlinx-coroutines-swing
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope.launch {
            LeakLensProjectService.getInstance(project).leaks.collectLatest { leaks ->
                ApplicationManager.getApplication().invokeLater {
                    mainPanel.refreshLeaks(leaks)
                }
            }
        }

        // Clean up coroutine scope when tool window is disposed
        toolWindow.contentManager.addContentManagerListener(object : com.intellij.ui.content.ContentManagerListener {
            override fun contentRemoved(event: com.intellij.ui.content.ContentManagerEvent) {
                scope.cancel()
                memoryPanel.dispose()
            }
        })
    }
}
