package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.AdbHeapDumpService
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.Timer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Left panel showing leak tree grouped by severity.
 * Enhanced with a "Quick Start" view when empty.
 */
class LeakTreePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val rootNode = DefaultMutableTreeNode("Leaks")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val statusLabel = JBLabel("Waiting for device...")
    private val countLabel = JBLabel("")
    private var connectivityTimer: Timer? = null

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)

    var onLeakSelected: ((LeakInfo) -> Unit)? = null

    private val quickStartPanel = panel {
        row {
            icon(AllIcons.General.Information).align(AlignX.CENTER)
        }
        row {
            label("No leaks detected yet").bold().align(AlignX.CENTER)
        }
        row {
            text("To get started:").align(AlignX.CENTER)
        }
        indent {
            row {
                link("1. Run your Android app in debug mode") {
                    // Help link
                }
            }
            row {
                link("2. Start real-time memory monitor") {
                    ActionManager.getInstance().tryToExecute(
                        ActionManager.getInstance().getAction("LeakLens.MonitorMemory"),
                        null,
                        null,
                        null,
                        true
                    )
                }
            }
            row {
                link("3. Click 'Dump Heap' to find leaks") {
                    ActionManager.getInstance().tryToExecute(
                        ActionManager.getInstance().getAction("LeakLens.DumpHeap"),
                        null,
                        null,
                        null,
                        true
                    )
                }
            }
        }
        row {
            label("💡 Pro Tip: LeakLens auto-detects LeakCanary logs!").applyToComponent {
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
                font = JBUI.Fonts.smallFont()
            }.align(AlignX.CENTER)
        }
    }.apply {
        border = JBUI.Borders.empty(20)
    }

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = LeakTreeCellRenderer()

        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val leak = node?.userObject as? LeakInfo
            if (leak != null) {
                onLeakSelected?.invoke(leak)
            }
        }

        contentPanel.add(JBScrollPane(tree), "TREE")
        contentPanel.add(quickStartPanel, "EMPTY")

        add(contentPanel, BorderLayout.CENTER)
        add(createToolbar(), BorderLayout.NORTH)
        add(createStatusBar(), BorderLayout.SOUTH)

        cardLayout.show(contentPanel, "EMPTY")

        startConnectivityWatcher()
    }

    private fun startConnectivityWatcher() {
        connectivityTimer = Timer(5000) {
            if (project.isDisposed) return@Timer

            ApplicationManager.getApplication().executeOnPooledThread {
                if (project.isDisposed) return@executeOnPooledThread

                try {
                    val adb = AdbHeapDumpService.getInstance(project)
                    val devices = adb.listDevices()

                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater

                        if (devices.isEmpty()) {
                            statusLabel.icon = AllIcons.General.Warning
                            statusLabel.text = "No device connected"
                        } else {
                            statusLabel.icon = AllIcons.General.InspectionsOK
                            statusLabel.text = "Connected: ${devices.first().take(8)}..."
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        connectivityTimer?.isRepeats = true
        connectivityTimer?.start()
    }

    fun updateLeaks(leaks: List<LeakInfo>) {
        if (project.isDisposed) return
        rootNode.removeAllChildren()

        if (leaks.isEmpty()) {
            cardLayout.show(contentPanel, "EMPTY")
            statusLabel.text = "All clear! ✅"
            countLabel.text = ""
            treeModel.reload()
            return
        }

        cardLayout.show(contentPanel, "TREE")

        // Group by severity
        val criticalNode =
            DefaultMutableTreeNode("Critical (${leaks.count { it.severity == LeakSeverity.CRITICAL }})")
        val warningNode =
            DefaultMutableTreeNode("Warning (${leaks.count { it.severity == LeakSeverity.WARNING }})")
        val libraryNode =
            DefaultMutableTreeNode("Library Leak (${leaks.count { it.severity == LeakSeverity.LIBRARY_LEAK }})")

        leaks.filter { it.severity == LeakSeverity.CRITICAL }
            .sortedBy { it.retainedObjectClassName }
            .forEach { criticalNode.add(DefaultMutableTreeNode(it)) }
        leaks.filter { it.severity == LeakSeverity.WARNING }
            .sortedBy { it.retainedObjectClassName }
            .forEach { warningNode.add(DefaultMutableTreeNode(it)) }
        leaks.filter { it.severity == LeakSeverity.LIBRARY_LEAK }
            .sortedBy { it.retainedObjectClassName }
            .forEach { libraryNode.add(DefaultMutableTreeNode(it)) }

        if (criticalNode.childCount > 0) rootNode.add(criticalNode)
        if (warningNode.childCount > 0) rootNode.add(warningNode)
        if (libraryNode.childCount > 0) rootNode.add(libraryNode)

        treeModel.reload()

        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }

        val totalRetained = leaks.sumOf { it.retainedByteSize }
        statusLabel.text = "${leaks.size} issues found"
        countLabel.text = "Retained: ${formatBytes(totalRetained)}"
    }

    private fun createToolbar(): JComponent {
        val actionManager = ActionManager.getInstance()
        val group = DefaultActionGroup().apply {
            add(actionManager.getAction("LeakLens.AnalyzeCurrentFile"))
            add(actionManager.getAction("LeakLens.AnalyzeProject"))
            addSeparator()
            add(actionManager.getAction("LeakLens.DumpHeap"))
            add(actionManager.getAction("LeakLens.MonitorMemory"))
            addSeparator()
            add(actionManager.getAction("LeakLens.ClearAll"))
        }

        val toolbar =
            actionManager.createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    private fun createStatusBar() = panel {
        separator()
        row {
            cell(statusLabel)
            cell(countLabel).align(AlignX.RIGHT)
        }
    }.apply {
        border = JBUI.Borders.empty(2, 8)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }

    override fun dispose() {
        connectivityTimer?.stop()
        connectivityTimer = null
    }

    private class LeakTreeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            val userObj = node.userObject

            when (userObj) {
                is LeakInfo -> {
                    icon = when (userObj.severity) {
                        LeakSeverity.CRITICAL -> AllIcons.General.Error
                        LeakSeverity.WARNING -> AllIcons.General.Warning
                        LeakSeverity.LIBRARY_LEAK -> AllIcons.General.Information
                    }
                    val simpleClassName = userObj.retainedObjectClassName.substringAfterLast('.')
                    append(simpleClassName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append(" - ${userObj.shortDescription}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    
                    if (userObj.retainedByteSize > 0) {
                        append(
                            " [${formatBytes(userObj.retainedByteSize)}]",
                            SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES
                        )
                    } else {
                        append(" [Static Scan]", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
                    }
                }
                is String -> {
                    icon = when {
                        userObj.startsWith("Critical") -> AllIcons.General.Error
                        userObj.startsWith("Warning") -> AllIcons.General.Warning
                        userObj.startsWith("Library Leak") -> AllIcons.General.Information
                        else -> null
                    }
                    append(userObj, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }
            }
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                bytes >= 1024 -> "${bytes / 1024} KB"
                else -> "$bytes B"
            }
        }
    }
}
