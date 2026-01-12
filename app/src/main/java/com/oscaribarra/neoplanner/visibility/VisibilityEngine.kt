package com.oscaribarra.neoplanner.visibility

/**
 * Module 3's job:
 * - given samples (already computed by astro later),
 * - return grouped windows + best window.
 */
object VisibilityEngine {

    data class Result(
        val windows: List<VisibilityWindow>,
        val best: VisibilityWindow?
    )

    fun evaluate(samples: List<VisibilitySample>, cfg: VisibilityConfig): Result {
        val windows = WindowGrouper.group(samples, cfg)
        val best = WindowGrouper.bestWindow(windows)
        return Result(windows = windows, best = best)
    }
}
