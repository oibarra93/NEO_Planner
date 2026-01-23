package com.oscaribarra.neoplanner.planner

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.oscaribarra.neoplanner.astro.coords.Pointing
import com.oscaribarra.neoplanner.astro.coords.SunAltitude
import com.oscaribarra.neoplanner.astro.coords.Topocentric
import com.oscaribarra.neoplanner.astro.orbit.GeoVector
import com.oscaribarra.neoplanner.astro.orbit.NeoWsOrbitMapper
import com.oscaribarra.neoplanner.astro.spk.De442sEphemeris
import com.oscaribarra.neoplanner.astro.spk.De442sManager
import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import com.oscaribarra.neoplanner.data.model.NeoWithOrbit
import com.oscaribarra.neoplanner.data.model.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class VisibilityPlanner(private val appContext: Context) {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun plan(
        observer: Observer,
        neos: List<NeoWithOrbit>,
        request: PlanRequest,
        nowLocal: ZonedDateTime = ZonedDateTime.now(ZoneId.of(observer.timeZoneId))
    ): List<PlannedNeoResult> = withContext(Dispatchers.Default) {

        // Load DE442s once per planning run
        val kernelFile = De442sManager(appContext).ensureKernel()
        val eph = De442sEphemeris(kernelFile)

        try {
            val zone = ZoneId.of(observer.timeZoneId)
            val nowUtcInstant = nowLocal.toInstant()

            val endUtcInstant = nowLocal
                .plusHours(request.hoursAhead.toLong())
                .toInstant()

            val stepSeconds = (request.stepMinutes.coerceAtLeast(1) * 60).toLong()

            val candidates = neos.take(request.maxNeos.coerceAtLeast(1))

            // Pre-map orbit elements once
            val mapped = candidates.map { neo ->
                neo to NeoWsOrbitMapper.fromNeoWsOrbit(neo.orbit)
            }

            // Iterate time samples
            val samples = buildSampleInstants(nowUtcInstant, endUtcInstant, stepSeconds)

            // Maintain per-NEO window tracking
            val trackers = mapped.associate { (neo, el) ->
                neo.id to WindowTracker(neo, el, zone)
            }

            for (tUtc in samples) {
                // Compute Sun altitude once per time
                val sunAltDeg = SunAltitude.sunAltitudeDeg(
                    eph = eph,
                    instantUtc = tUtc,
                    obsLatDeg = observer.latitudeDeg,
                    obsLonDeg = observer.longitudeDeg,
                    obsHeightMeters = observer.elevationMeters
                )

                // Compute Earth heliocentric (Earth wrt Sun) once per time for all NEOs
                val earthSun = earthWrtSunKm(eph, tUtc)

                val isDarkEnough = sunAltDeg <= request.twilightLimitDeg

                for ((_, tracker) in trackers) {
                    // NEO geocentric inertial vector
                    val geo = neoGeocentricFromEarthSun(
                        tracker = tracker,
                        instantUtc = tUtc,
                        earthSunKm = earthSun
                    )

                    if (geo == null) {
                        tracker.onSampleNotVisible(tUtc)
                        continue
                    }

                    val topo = Topocentric.solve(
                        geocentricEciKm = geo,
                        instantUtc = tUtc,
                        obsLatDeg = observer.latitudeDeg,
                        obsLonDeg = observer.longitudeDeg,
                        obsHeightMeters = observer.elevationMeters
                    )

                    val alt = topo.altAz.altitudeDeg
                    val az = topo.altAz.azimuthDeg
                    val visible = isDarkEnough && alt >= request.minAltDeg

                    if (visible) {
                        tracker.onSampleVisible(tUtc, alt, az)
                    } else {
                        tracker.onSampleNotVisible(tUtc)
                    }
                }
            }

            // Finalize any open windows and produce results
            trackers.values.map { it.buildResult() }
                // Useful: sort by best peak altitude desc (nulls last)
                .sortedWith(compareByDescending<PlannedNeoResult> { it.peakAltitudeDeg ?: Double.NEGATIVE_INFINITY }
                    .thenBy { it.neo.name })

        } finally {
            eph.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildSampleInstants(startUtc: Instant, endUtc: Instant, stepSeconds: Long): List<Instant> {
        val out = ArrayList<Instant>(1024)
        var t = startUtc
        while (!t.isAfter(endUtc)) {
            out.add(t)
            t = t.plusSeconds(stepSeconds)
        }
        return out
    }

    /**
     * Earth wrt Sun (km) in inertial equatorial frame:
     * EarthSun = EarthSSB - SunSSB
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun earthWrtSunKm(eph: De442sEphemeris, tUtc: Instant): Vec3Km {
        val et = GeoVector.etSecondsFromUtc(tUtc)
        val earthSsb = eph.earthWrtSsb(et)
        val sunSsb = eph.position(10, 0, et)
        return Vec3Km(
            earthSsb.x - sunSsb.x,
            earthSsb.y - sunSsb.y,
            earthSsb.z - sunSsb.z
        )
    }

    /**
     * Build NEO geocentric vector using precomputed EarthSun.
     * NEOgeo = NEOhelio - EarthSun
     */
    private fun neoGeocentricFromEarthSun(
        tracker: WindowTracker,
        instantUtc: Instant,
        earthSunKm: Vec3Km
    ): Vec3Km? {
        val neoHelio = tracker.propagateHeliocentricKm(instantUtc) ?: return null
        return Vec3Km(
            neoHelio.x - earthSunKm.x,
            neoHelio.y - earthSunKm.y,
            neoHelio.z - earthSunKm.z
        )
    }

    private class WindowTracker(
        val neo: NeoWithOrbit,
        val elements: com.oscaribarra.neoplanner.astro.orbit.OrbitElements,
        val zone: ZoneId
    ) {
        private var currentStartUtc: Instant? = null
        private var currentPeakAlt: Double = Double.NEGATIVE_INFINITY
        private var currentPeakTimeUtc: Instant? = null
        private var currentPeakAz: Double? = null

        private val windows = ArrayList<WindowSummary>()

        @RequiresApi(Build.VERSION_CODES.O)
        fun propagateHeliocentricKm(tUtc: Instant): Vec3Km? {
            // Use Module 8 propagator directly (already used inside GeoVector in your debug),
            // but here we avoid recomputing EarthSun each time.
            return com.oscaribarra.neoplanner.astro.orbit.OrbitPropagator.heliocentricEquatorialKm(elements, tUtc)
        }

        fun onSampleVisible(tUtc: Instant, altDeg: Double, azDeg: Double) {
            if (currentStartUtc == null) {
                currentStartUtc = tUtc
                currentPeakAlt = altDeg
                currentPeakTimeUtc = tUtc
                currentPeakAz = azDeg
            } else {
                if (altDeg > currentPeakAlt) {
                    currentPeakAlt = altDeg
                    currentPeakTimeUtc = tUtc
                    currentPeakAz = azDeg
                }
            }
        }

        fun onSampleNotVisible(tUtc: Instant) {
            // Close current window if open
            val start = currentStartUtc ?: return
            val end = tUtc // end at the first non-visible sample time
            windows.add(
                WindowSummary(
                    startUtc = start,
                    endUtc = end,
                    peakAltDeg = currentPeakAlt,
                    peakTimeUtc = currentPeakTimeUtc,
                    peakAzDeg = currentPeakAz
                )
            )
            resetCurrent()
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun buildResult(): PlannedNeoResult {
            // If still open, close at last known peak time + 0 (best effort)
            if (currentStartUtc != null) {
                windows.add(
                    WindowSummary(
                        startUtc = currentStartUtc!!,
                        endUtc = currentPeakTimeUtc ?: currentStartUtc!!,
                        peakAltDeg = currentPeakAlt,
                        peakTimeUtc = currentPeakTimeUtc,
                        peakAzDeg = currentPeakAz
                    )
                )
                resetCurrent()
            }

            val best = windows.maxByOrNull { it.peakAltDeg }

            val bestStartLocal = best?.startUtc?.atZone(zone)
            val bestEndLocal = best?.endUtc?.atZone(zone)
            val peakTimeLocal = best?.peakTimeUtc?.atZone(zone)

            val peakAlt = best?.peakAltDeg
            val peakAz = best?.peakAzDeg

            val cardinal = if (peakAz != null) Pointing.cardinalFromAzDeg(peakAz) else null
            val hint = if (peakAlt != null && peakAz != null) Pointing.hintFromAltAz(peakAlt, peakAz) else null

            return PlannedNeoResult(
                neo = neo,
                bestStartLocal = bestStartLocal,
                bestEndLocal = bestEndLocal,
                peakTimeLocal = peakTimeLocal,
                peakAltitudeDeg = peakAlt,
                peakAzimuthDeg = peakAz,
                peakCardinal = cardinal,
                pointingHint = hint,
                visibleWindowCount = windows.size
            )
        }

        private fun resetCurrent() {
            currentStartUtc = null
            currentPeakAlt = Double.NEGATIVE_INFINITY
            currentPeakTimeUtc = null
            currentPeakAz = null
        }

        private data class WindowSummary(
            val startUtc: Instant,
            val endUtc: Instant,
            val peakAltDeg: Double,
            val peakTimeUtc: Instant?,
            val peakAzDeg: Double?
        )
    }
}
