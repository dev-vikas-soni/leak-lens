package com.github.devvikassoni.leaklens.compat

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager

/**
 * Facade for progress reporting to allow for adaptation if progress APIs evolve.
 */
object ProgressFacade {
    fun checkCanceled(indicator: ProgressIndicator? = null) {
        if (indicator != null) {
            indicator.checkCanceled()
        } else {
            ProgressManager.checkCanceled()
        }
    }

    fun setText(indicator: ProgressIndicator?, text: String) {
        indicator?.text = text
    }

    fun setFraction(indicator: ProgressIndicator?, fraction: Double) {
        indicator?.fraction = fraction
    }
}
