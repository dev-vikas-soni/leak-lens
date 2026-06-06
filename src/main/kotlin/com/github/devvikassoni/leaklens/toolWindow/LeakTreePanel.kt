package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
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
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.Timer
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Left panel showing leak tree grouped by severity.
 * 
 * Updated with UI DSL 2 for better alignment.
 */
class LeakTreePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val rootNode = DefaultMutableTreeNode("Leaks")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val statusLabel = JBLabel("Waiting for heap dump...")
    private val countLabel = JBLabel("")
    private var connectivityTimer: Timer? = null

    var onLeakSelected: ((LeakInfo) -> Unit)? = null

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = LeakTreeCellRenderer()

        tree.addTreeSelectionListener { e ->
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val leak = node?.userObject as? LeakInfo
            if (leak != null) {
                onLeakSelected?.invoke(leak)
            }
        }

        add(JBScrollPane(tree), BorderLayout.CENTER)
        add(createToolbar(), BorderLayout.NORTH)
        add(createStatusBar(), BorderLayout.SOUTH)

        // Start device connectivity watcher
        startConnectivityWatcher()
    }

    private fun startConnectivityWatcher() {
        connectivityTimer = Timer(5000) {
            if (project.isDisposed) return@Timer

            com.intellij.openapi.application.ApplicationManager.getApplication()
                .executeOnPooledThread {
                    if (project.isDisposed) return@executeOnPooledThread

                    try {
                        val adb =
                            com.github.devvikassoni.leaklens.services.AdbHeapDumpService.getInstance(
                                project
                            )
                        val devices = adb.listDevices()

                        com.intellij.openapi.application.ApplicationManager.getApplication()
                            .invokeLater {
                                if (project.isDisposed) return@invokeLater

                                if (devices.isEmpty()) {
                                    statusLabel.icon = com.intellij.icons.AllIcons.General.Warning
                                    statusLabel.text = "No device connected"
                                } else {
                                    statusLabel.icon =
                                        com.intellij.icons.AllIcons.General.InspectionsOK
                                    statusLabel.text = "Monitoring ${devices.size} device(s)"
                                }
                            }
                    } catch (_: Exception) {
                        // Project likely disposed or service unavailable
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
            statusLabel.text = "No leaks detected ✅"
            countLabel.text = ""
            treeModel.reload()
            return
        }

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

        // Expand all
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }

        val totalRetained = leaks.sumOf { it.retainedByteSize }
        statusLabel.text = "${leaks.size} leak(s) detected"
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
        toolbar.targetComponent = tree
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
                        LeakSeverity.CRITICAL -> com.intellij.icons.AllIcons.General.Error
                        LeakSeverity.WARNING -> com.intellij.icons.AllIcons.General.Warning
                        LeakSeverity.LIBRARY_LEAK -> com.intellij.icons.AllIcons.General.Information
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
                        userObj.startsWith("Critical") -> com.intellij.icons.AllIcons.General.Error
                        userObj.startsWith("Warning") -> com.intellij.icons.AllIcons.General.Warning
                        userObj.startsWith("Library Leak") -> com.intellij.icons.AllIcons.General.Information
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
