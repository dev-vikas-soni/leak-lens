package com.github.devvikassoni.leaklens.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import javax.swing.JLabel
import javax.swing.JPanel

class LeakPilotToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val panel = JPanel()

        panel.add(
            JLabel("LeakPilot is alive 🚀")
        )

        val content =
            toolWindow.contentManager.factory
                .createContent(panel, "", false)

        toolWindow.contentManager.addContent(content)
    }
}