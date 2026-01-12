package com.oscaribarra.neoplanner.visibility

object WindowGrouper {

    /**
     * Groups contiguous visible samples into windows.
     *
     * Contiguous means consecutive indices in the samples list.
     * Windows are inclusive of start/end sample times.
     */
    fun group(
        samples: List<VisibilitySample>,
        cfg: VisibilityConfig
    ): List<VisibilityWindow> {
        if (samples.isEmpty()) return emptyList()

        val out = mutableListOf<VisibilityWindow>()
        var i = 0

        while (i < samples.size) {
            val s = samples[i]
            val vis = s.isFinite && VisibilityRules.isVisible(
                neoAltDeg = s.neoAltDeg,
                sunAltDeg = s.sunAltDeg,
                minAltDeg = cfg.minAltDeg,
                twilightLimitDeg = cfg.twilightLimitDeg
            )

            if (!vis) {
                i++
                continue
            }

            val startIdx = i
            var peakIdx = i

            i++
            while (i < samples.size) {
                val si = samples[i]
                val visi = si.isFinite && VisibilityRules.isVisible(
                    neoAltDeg = si.neoAltDeg,
                    sunAltDeg = si.sunAltDeg,
                    minAltDeg = cfg.minAltDeg,
                    twilightLimitDeg = cfg.twilightLimitDeg
                )
                if (!visi) break

                if (si.neoAltDeg > samples[peakIdx].neoAltDeg) peakIdx = i
                i++
            }

            val endIdx = i - 1
            val start = samples[startIdx].t
            val end = samples[endIdx].t
            val peakS = samples[peakIdx]

            out += VisibilityWindow(
                start = start,
                end = end,
                peak = WindowPoint(peakS.t, peakS.neoAltDeg, peakS.neoAzDeg)
            )
        }

        return out
    }

    /**
     * Best window = the one with the highest peak altitude.
     * If tie, pick the earliest peak time.
     */
    fun bestWindow(windows: List<VisibilityWindow>): VisibilityWindow? {
        return windows
            .sortedWith(
                compareByDescending<VisibilityWindow> { it.peakAltDeg }
                    .thenBy { it.peakTime }
            )
            .firstOrNull()
    }
}
