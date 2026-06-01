package com.github.devvikassoni.leaklens.toolWindow

import com.github.devvikassoni.leaklens.model.LeakInfo
import com.github.devvikassoni.leaklens.model.LeakSeverity
import com.github.devvikassoni.leaklens.services.LeakLensProjectService
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Left panel showing leak tree grouped by severity.
 */
class LeakTreePanel(private val project: Project) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Leaks")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)
    private val statusLabel = JLabel("Waiting for heap dump...")
    private val countLabel = JLabel("")

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
    }

    fun updateLeaks(leaks: List<LeakInfo>) {
        rootNode.removeAllChildren()

        if (leaks.isEmpty()) {
            statusLabel.text = "No leaks detected ✅"
            countLabel.text = ""
            treeModel.reload()
            return
        }

        // Group by severity
        val criticalNode = DefaultMutableTreeNode("🔴 Critical (${leaks.count { it.severity == LeakSeverity.CRITICAL }})")
        val warningNode = DefaultMutableTreeNode("🟡 Warning (${leaks.count { it.severity == LeakSeverity.WARNING }})")
        val libraryNode = DefaultMutableTreeNode("🟢 Library Leak (${leaks.count { it.severity == LeakSeverity.LIBRARY_LEAK }})")

        leaks.filter { it.severity == LeakSeverity.CRITICAL }.forEach { criticalNode.add(DefaultMutableTreeNode(it)) }
        leaks.filter { it.severity == LeakSeverity.WARNING }.forEach { warningNode.add(DefaultMutableTreeNode(it)) }
        leaks.filter { it.severity == LeakSeverity.LIBRARY_LEAK }.forEach { libraryNode.add(DefaultMutableTreeNode(it)) }

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

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))

        val clearButton = JButton("Clear").apply {
            toolTipText = "Clear all leaks"
            addActionListener {
                LeakLensProjectService.getInstance(project).clearLeaks()
                updateLeaks(emptyList())
                onLeakSelected?.invoke(LeakInfo("", "", "", "", 0, 0, LeakSeverity.WARNING, emptyList())) // trigger empty state
            }
        }

        val refreshLabel = JLabel("LeakLens")
        refreshLabel.font = refreshLabel.font.deriveFont(java.awt.Font.BOLD)

        toolbar.add(refreshLabel)
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(clearButton)
        return toolbar
    }

    private fun createStatusBar(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(statusLabel, BorderLayout.WEST)
        panel.add(countLabel, BorderLayout.EAST)
        return panel
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
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
                    val simpleClassName = userObj.retainedObjectClassName.substringAfterLast('.')
                    append(simpleClassName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append(" - ${userObj.shortDescription}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append("  [${formatBytes(userObj.retainedByteSize)}]", SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
                }
                is String -> {
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

