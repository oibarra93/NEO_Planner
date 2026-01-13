package com.oscaribarra.neoplanner.astro.orbit

import com.oscaribarra.neoplanner.data.model.OrbitElements as NeoWsOrbitElements

object NeoWsOrbitMapper {

    fun fromNeoWsOrbit(o: NeoWsOrbitElements): OrbitElements {
        return OrbitElements(
            epochJd = o.epochJd,
            e = o.e,
            aAu = o.aAu,
            iDeg = o.iDeg,
            raanDeg = o.raanDeg,
            argPeriDeg = o.argPeriDeg,
            meanAnomalyDeg = o.meanAnomDeg,
            meanMotionDegPerDay = o.meanMotionDegPerDay
        )
    }
}
