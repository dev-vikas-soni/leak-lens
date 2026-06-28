package com.github.devvikassoni.leaklens.ui;

import com.github.devvikassoni.leaklens.services.LeakLensProjectService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

public class LeakLensStatusBarWidgetFactory implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
        return "LeakLensStatusBarWidget";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "LeakLens Status";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new LeakLensStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        Disposer.dispose(widget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }

    private static class LeakLensStatusBarWidget implements StatusBarWidget, CustomStatusBarWidget {
        private final Project project;
        private final JBLabel component;

        public LeakLensStatusBarWidget(Project project) {
            this.project = project;
            this.component = new JBLabel();
            this.component.setFont(JBUI.Fonts.smallFont());
            this.component.setBorder(JBUI.Borders.empty(0, 4));
            this.component.setIcon(AllIcons.General.Warning);
            this.component.setText("LeakLens: 0");
            this.component.setToolTipText("Click to open LeakLens dashboard");
            this.component.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                            .getToolWindow("LeakLens").show();
                }
            });

            startObservation();
        }

        private void startObservation() {
            LeakLensProjectService service = LeakLensProjectService.Companion.getInstance(project);
            // Use ApplicationManager to run the flow collection in background
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                // Collect flows from the Kotlin service. 
                // Since this is Java, we'll just check periodically or use a listener if we had one.
                // For a robust "real-time" feel without complex Flow-to-Java bridges, 
                // a 3-second poll is lightweight and effective.
                while (!project.isDisposed()) {
                    int total = service.getLeaks().getValue().size() +
                            service.getLiveIssues().getValue().size();
                    updateUI(total);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }

        private void updateUI(int total) {
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) return;
                if (total == 0) {
                    component.setText("LeakLens: OK");
                    component.setIcon(AllIcons.General.InspectionsOK);
                } else {
                    component.setText("LeakLens: " + total);
                    component.setIcon(AllIcons.General.Warning);
                }
            });
        }

        @Override
        public @NotNull String ID() {
            return "LeakLensStatusBarWidget";
        }

        @Override
        public JComponent getComponent() {
            return component;
        }

        @Override
        public void install(@NotNull StatusBar statusBar) {
        }

        @Override
        public void dispose() {
        }
    }
}
