package com.github.devvikassoni.leaklens.monitoring

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import javax.swing.SwingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Real-time memory graph panel (lite profiler).
 * Shows Java heap, native heap, and total PSS over time.
 *
 * Modernized to use JBPanel and direct reactive Flow repaint triggers,
 * and enhanced with tooltips and clearer visual cues.
 */
class MemoryGraphPanel(private val project: Project) : JBPanel<MemoryGraphPanel>(BorderLayout()),
    Disposable {

    private val monitor = DeviceMemoryMonitor.getInstance(project)
    private val graphData = MutableStateFlow<List<DeviceMemoryMonitor.MemorySnapshot>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Theme-aware colors
    private val javaHeapColor = JBColor(Color(0x4C, 0xAF, 0x50), Color(0x66, 0xBB, 0x6A))
    private val nativeHeapColor = JBColor(Color(0xFF, 0x98, 0x00), Color(0xFF, 0xB7, 0x4D))
    private val totalPssColor = JBColor(Color(0x21, 0x96, 0xF3), Color(0x64, 0xB5, 0xF6))
    private val gridColor = JBColor(Color(0, 0, 0, 20), Color(255, 255, 255, 20))

    private val graphCanvas = object : JBPanel<JBPanel<*>>() {
        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            renderGraph(g as Graphics2D)
        }
    }

    init {
        preferredSize = Dimension(400, 200)
        background = JBColor.background()
        border = JBUI.Borders.empty(10)

        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(
                JBLabel("Real-time Memory (Lite Profiler)", SwingConstants.LEFT).apply {
                    font = JBFont.label().asBold()
                },
                BorderLayout.WEST
            )
            add(
                JBLabel("Updating every 5s", SwingConstants.RIGHT).apply {
                    font = JBFont.small()
                    foreground = JBColor.GRAY
                },
                BorderLayout.EAST
            )
        }

        add(header, BorderLayout.NORTH)
        add(graphCanvas, BorderLayout.CENTER)

        // Poll snapshots reactively
        scope.launch {
            monitor.memorySnapshots.collectLatest { snapshots ->
                graphData.update { snapshots }
                refreshUI()
            }
        }

        // Listen for status changes
        scope.launch {
            monitor.status.collectLatest {
                refreshUI()
            }
        }
    }

    private fun refreshUI() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                updateTooltip()
                graphCanvas.repaint()
            }
        }
    }

    private fun updateTooltip() {
        val current = graphData.value.lastOrNull()
        if (current != null) {
            toolTipText = """
                <html>
                <b>Current Memory Stats:</b><br/>
                Java Heap: ${current.javaHeap / 1024} MB<br/>
                Native Heap: ${current.nativeHeap / 1024} MB<br/>
                Total PSS: ${current.totalPss / 1024} MB<br/>
                Active Activities: ${current.activities}
                </html>
            """.trimIndent()
        } else {
            toolTipText = "Start monitoring to see memory trends"
        }
    }

    private fun renderGraph(g2: Graphics2D) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        )

        val w = graphCanvas.width
        val h = graphCanvas.height
        val paddingLeft = 45
        val paddingRight = 10
        val paddingTop = 25
        val paddingBottom = 40

        val graphW = w - paddingLeft - paddingRight
        val graphH = h - paddingTop - paddingBottom

        // Always draw Background Grid to look "connected"
        g2.color = gridColor
        for (i in 0..4) {
            val y = paddingTop + (graphH * i / 4)
            g2.drawLine(paddingLeft, y, w - paddingRight, y)
        }

        // Always draw axes
        g2.color = JBColor.border()
        g2.drawLine(paddingLeft, h - paddingBottom, w - paddingRight, h - paddingBottom) // X
        g2.drawLine(paddingLeft, paddingTop, paddingLeft, h - paddingBottom) // Y

        val data = graphData.value
        if (data.isEmpty()) {
            g2.color = JBColor.GRAY
            g2.font = JBFont.regular()

            val status = monitor.status.value
            val message = when (status) {
                DeviceMemoryMonitor.Status.ERROR ->
                    "Error: ${monitor.lastError.value ?: "ADB connection failed"}"

                DeviceMemoryMonitor.Status.DISCONNECTED ->
                    "Monitor stopped. Click 'Start' to begin."

                DeviceMemoryMonitor.Status.CONNECTED ->
                    "Connecting to process..."
            }

            g2.drawString(message, paddingLeft + 10, h / 2)
            return
        }

        val maxVal = data.maxOf { maxOf(it.totalPss, it.javaHeap + it.nativeHeap) }
            .coerceAtLeast(1024L * 10)

        // Y-axis labels
        g2.font = JBFont.small()
        g2.color = JBColor.GRAY
        g2.drawString("${maxVal / 1024}MB", 2, paddingTop + 5)
        g2.drawString("${(maxVal / 2) / 1024}MB", 2, paddingTop + (graphH / 2) + 5)
        g2.drawString("0", 2, h - paddingBottom)

        // Draw lines with area fill
        val stepX = graphW.toFloat() / (data.size - 1).coerceAtLeast(1)

        drawMemoryLine(
            g2,
            data.map { it.totalPss },
            maxVal,
            stepX,
            graphH,
            paddingLeft,
            paddingTop,
            totalPssColor,
            fill = true
        )
        drawMemoryLine(
            g2,
            data.map { it.javaHeap },
            maxVal,
            stepX,
            graphH,
            paddingLeft,
            paddingTop,
            javaHeapColor,
            fill = false
        )
        drawMemoryLine(
            g2,
            data.map { it.nativeHeap },
            maxVal,
            stepX,
            graphH,
            paddingLeft,
            paddingTop,
            nativeHeapColor,
            fill = false
        )

        // Modern Legend (Bottom)
        val legendX = paddingLeft
        val legendY = h - 15
        renderLegendItem(g2, legendX, legendY, totalPssColor, "Total PSS")
        renderLegendItem(g2, legendX + 90, legendY, javaHeapColor, "Java Heap")
        renderLegendItem(g2, legendX + 185, legendY, nativeHeapColor, "Native")

        // Current summary text
        val current = data.lastOrNull()
        if (current != null) {
            g2.color = JBColor.foreground()
            g2.font = JBFont.small().asBold()
            val summary =
                "Total: ${current.totalPss / 1024}MB | Java: ${current.javaHeap / 1024}MB"
            val metrics = g2.fontMetrics
            g2.drawString(summary, w - metrics.stringWidth(summary) - paddingRight, legendY)
        }
    }

    private fun renderLegendItem(g2: Graphics2D, x: Int, y: Int, color: Color, label: String) {
        g2.color = color
        g2.fillRoundRect(x, y - 8, 8, 8, 2, 2)
        g2.color = JBColor.foreground()
        g2.font = JBFont.small()
        g2.drawString(label, x + 12, y)
    }

    private fun drawMemoryLine(
        g2: Graphics2D,
        values: List<Long>,
        maxVal: Long,
        stepX: Float,
        graphH: Int,
        paddingLeft: Int,
        paddingTop: Int,
        color: Color,
        fill: Boolean
    ) {
        if (values.size < 2) return

        val xPoints = IntArray(values.size)
        val yPoints = IntArray(values.size)

        for (i in values.indices) {
            xPoints[i] = paddingLeft + (i * stepX).toInt()
            yPoints[i] = paddingTop + graphH - ((values[i].toFloat() / maxVal) * graphH).toInt()
        }

        if (fill) {
            val fillPath = Polygon()
            fillPath.addPoint(xPoints[0], paddingTop + graphH)
            for (i in xPoints.indices) fillPath.addPoint(xPoints[i], yPoints[i])
            fillPath.addPoint(xPoints.last(), paddingTop + graphH)

            g2.color = Color(color.red, color.green, color.blue, 30)
            g2.fill(fillPath)
        }

        g2.color = color
        g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawPolyline(xPoints, yPoints, values.size)
    }

    override fun dispose() {
        scope.cancel()
    }
}
