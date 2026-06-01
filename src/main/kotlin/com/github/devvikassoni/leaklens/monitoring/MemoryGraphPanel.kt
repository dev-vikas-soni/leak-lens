package com.github.devvikassoni.leaklens.monitoring

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Real-time memory graph panel (lite profiler).
 * Shows Java heap, native heap, and total PSS over time.
 */
class MemoryGraphPanel(private val project: Project) : JPanel() {

    private val monitor = DeviceMemoryMonitor.getInstance(project)
    private val graphData = mutableListOf<DeviceMemoryMonitor.MemorySnapshot>()
    private val refreshTimer = Timer(1000) { repaint() }

    private val javaHeapColor = JBColor(Color(0x4C, 0xAF, 0x50), Color(0x66, 0xBB, 0x6A))
    private val nativeHeapColor = JBColor(Color(0xFF, 0x98, 0x00), Color(0xFF, 0xB7, 0x4D))
    private val totalPssColor = JBColor(Color(0x21, 0x96, 0xF3), Color(0x64, 0xB5, 0xF6))

    init {
        preferredSize = Dimension(400, 150)
        background = JBColor.background()
        refreshTimer.start()

        // Poll snapshots
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope.launch {
            monitor.memorySnapshots.collectLatest { snapshots ->
                synchronized(graphData) {
                    graphData.clear()
                    graphData.addAll(snapshots)
                }
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val w = width
        val h = height
        val padding = 40

        synchronized(graphData) {
            if (graphData.isEmpty()) {
                g2.color = JBColor.GRAY
                g2.font = Font("SansSerif", Font.PLAIN, 12)
                g2.drawString("No memory data. Start monitoring to see graph.", padding, h / 2)
                return
            }

            val maxVal = graphData.maxOf { maxOf(it.totalPss, it.javaHeap + it.nativeHeap) }.coerceAtLeast(1)
            val graphW = w - padding * 2
            val graphH = h - padding * 2

            // Draw axes
            g2.color = JBColor.GRAY
            g2.drawLine(padding, h - padding, w - padding, h - padding) // X
            g2.drawLine(padding, padding, padding, h - padding) // Y

            // Y-axis labels
            g2.font = Font("SansSerif", Font.PLAIN, 10)
            g2.drawString("${maxVal / 1024} MB", 2, padding + 10)
            g2.drawString("0", 2, h - padding)

            // Draw lines
            val stepX = graphW.toFloat() / (graphData.size - 1).coerceAtLeast(1)

            drawLine(g2, graphData.map { it.totalPss }, maxVal, stepX, graphW, graphH, padding, totalPssColor)
            drawLine(g2, graphData.map { it.javaHeap }, maxVal, stepX, graphW, graphH, padding, javaHeapColor)
            drawLine(g2, graphData.map { it.nativeHeap }, maxVal, stepX, graphW, graphH, padding, nativeHeapColor)

            // Legend
            val legendY = padding - 10
            g2.color = totalPssColor; g2.fillRect(padding, legendY, 10, 10)
            g2.color = JBColor.foreground(); g2.drawString("Total PSS", padding + 14, legendY + 9)
            g2.color = javaHeapColor; g2.fillRect(padding + 90, legendY, 10, 10)
            g2.color = JBColor.foreground(); g2.drawString("Java Heap", padding + 104, legendY + 9)
            g2.color = nativeHeapColor; g2.fillRect(padding + 185, legendY, 10, 10)
            g2.color = JBColor.foreground(); g2.drawString("Native", padding + 199, legendY + 9)

            // Current value
            val current = graphData.lastOrNull()
            if (current != null) {
                g2.color = JBColor.foreground()
                g2.drawString(
                    "Java: ${current.javaHeap / 1024}MB | Native: ${current.nativeHeap / 1024}MB | Total: ${current.totalPss / 1024}MB | Activities: ${current.activities}",
                    padding, h - 5
                )
            }
        }
    }

    private fun drawLine(g2: Graphics2D, values: List<Long>, maxVal: Long, stepX: Float, graphW: Int, graphH: Int, padding: Int, color: Color) {
        if (values.size < 2) return
        g2.color = color
        g2.stroke = BasicStroke(2f)

        for (i in 1 until values.size) {
            val x1 = padding + ((i - 1) * stepX).toInt()
            val y1 = padding + graphH - ((values[i - 1].toFloat() / maxVal) * graphH).toInt()
            val x2 = padding + (i * stepX).toInt()
            val y2 = padding + graphH - ((values[i].toFloat() / maxVal) * graphH).toInt()
            g2.drawLine(x1, y1, x2, y2)
        }
    }

    fun dispose() {
        refreshTimer.stop()
    }
}

