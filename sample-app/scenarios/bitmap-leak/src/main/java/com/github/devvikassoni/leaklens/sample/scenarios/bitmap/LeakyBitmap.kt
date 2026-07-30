package com.github.devvikassoni.leaklens.sample.scenarios.bitmap

import android.graphics.Bitmap
import com.github.devvikassoni.leaklens.sample.core.common.LeakScenario

/**
 * SCENARIO: Large Bitmap Retention
 *
 * Holding a reference to a large bitmap in a long-lived singleton or static field.
 */
object BitmapCache {
    // RED FLAG: Static cache that never clears
    val bitmaps = mutableListOf<Bitmap>()
}

object BitmapScenario : LeakScenario {
    override val id = "bitmap-leak"
    override val title = "Large Bitmap Leak"
    override val description = "Retaining high-resolution bitmaps in a static list."
    override val leakSource = "BitmapCache.bitmaps"
    override val expectedInspection = "LeakLensStaticActivityReference" // Reuse or add specific
    override val reproductionSteps = "1. Load bitmaps. 2. Clear screen. 3. Dump heap."
    override val fixSuggestion = "Use LruCache or WeakReference for bitmap storage."
}
