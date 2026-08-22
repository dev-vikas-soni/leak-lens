package com.github.devvikassoni.leaklens.ui

import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

class LeakLensStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "LeakLensStatusBarWidget"
    override fun getDisplayName() = "LeakLens Status"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = LeakLensStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {}
    override fun canBeEnabledOn(statusBar: StatusBar) = true

    private class LeakLensStatusBarWidget(private val project: Project) :
        StatusBarWidget,
        CustomStatusBarWidget {
        private val component = JBLabel().apply {
            font = JBUI.Fonts.smallFont()
            border = JBUI.Borders.empty(0, 4)
            icon = AllIcons.General.Warning
            text = "LeakLens: 0"
            toolTipText = "Click to open LeakLens dashboard"
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                        .getToolWindow("LeakLens")?.show()
                }
            })
        }

        init {
            val service = LeakLensProjectService.getInstance(project)
            combine(service.leaks, service.liveIssues) { heapLeaks, liveIssues ->
                heapLeaks.size + liveIssues.size
            }.onEach { total ->
                updateUI(total)
            }.launchIn(service.scope)
        }

        private fun updateUI(total: Int) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (total == 0) {
                    component.text = "LeakLens: healthy"
                    component.icon = AllIcons.General.InspectionsOK
                } else {
                    component.text = "LeakLens: $total issues"
                    component.icon = AllIcons.General.Warning
                }
            }
        }

        override fun ID() = "LeakLensStatusBarWidget"
        override fun getComponent(): JComponent = component
        override fun install(statusBar: StatusBar) {}
        override fun dispose() {}
    }
}
