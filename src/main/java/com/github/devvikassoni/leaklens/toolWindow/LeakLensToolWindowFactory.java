package com.github.devvikassoni.leaklens.toolWindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import org.jetbrains.annotations.NotNull;

public class LeakLensToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        LeakListPanel leakListPanel = new LeakListPanel(project);
        LeakDetailPanel leakDetailPanel = new LeakDetailPanel(project);
        LeakLensMainPanel mainPanel = new LeakLensMainPanel(project, leakListPanel, leakDetailPanel);

        // Tab 1: Leak Analysis
        Content leakContent = ContentFactory.getInstance().createContent(mainPanel, "LeakLens", false);
        toolWindow.getContentManager().addContent(leakContent);
        Disposer.register(leakContent, mainPanel);
    }
}
