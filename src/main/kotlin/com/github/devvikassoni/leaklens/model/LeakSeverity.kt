package com.github.devvikassoni.leaklens.model

import com.intellij.ui.JBColor
import java.awt.Color

enum class LeakSeverity(val displayName: String, val color: JBColor) {
    CRITICAL("Critical", JBColor(Color(220, 50, 50), Color(220, 80, 80))),
    WARNING("Warning", JBColor(Color(220, 180, 50), Color(220, 200, 80))),
    LIBRARY_LEAK("Library Leak", JBColor(Color(80, 180, 80), Color(100, 200, 100)))
}
