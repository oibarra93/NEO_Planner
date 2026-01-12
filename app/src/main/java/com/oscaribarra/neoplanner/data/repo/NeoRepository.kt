package com.oscaribarra.neoplanner.data.repo

import com.oscaribarra.neoplanner.data.model.NeoCandidate
import com.oscaribarra.neoplanner.data.model.NeoWithOrbit
import com.oscaribarra.neoplanner.data.model.OrbitElements
import com.oscaribarra.neoplanner.data.neows.NeoWsClient
import com.oscaribarra.neoplanner.data.neows.NeoFeedItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NeoRepository(
    private val neoWs: NeoWsClient
) {
    /**
     * Fetch feed, flatten entries, pick closest approach within window, return list sorted by closest approach time.
     */
    suspend fun fetchCandidates(startDate: String, endDate: String): List<NeoCandidate> {
        val feed = neoWs.fetchFeed(startDate, endDate)
        val all = feed.neosByDate.values.flatten()

        val candidates = all.mapNotNull { it.toCandidateOrNull() }

        return candidates.sortedBy { it.closestApproachUtcIso ?: "9999" }
    }

    /**
     * Fetch details for top N candidates (or fewer) and parse orbital elements.
     */
    suspend fun fetchDetails(
        candidates: List<NeoCandidate>,
        maxNeos: Int,
        zoneId: ZoneId
    ): List<NeoWithOrbit> {
        val limited = candidates.take(maxNeos)

        return limited.map { c ->
            val detail = neoWs.fetchNeoDetail(c.id)
            val od = detail.orbitalData

            val orbit = OrbitElements(
                epochJd = od.epochJd.toDouble(),
                e = od.eccentricity.toDouble(),
                aAu = od.semiMajorAxisAu.toDouble(),
                iDeg = od.inclination.toDouble(),
                raanDeg = od.ascendingNodeLongitude.toDouble(),
                argPeriDeg = od.perihelionArgument.toDouble(),
                meanAnomDeg = od.meanAnomaly.toDouble(),
                meanMotionDegPerDay = od.meanMotionDegPerDay?.toDoubleOrNull()
            )

            val closestLocal = c.closestApproachUtcIso?.let { utcIso ->
                parseUtcIsoToLocal(utcIso, zoneId)
            }

            NeoWithOrbit(
                id = detail.id,
                name = detail.name,
                hMagnitude = detail.absoluteMagnitudeH,
                isHazardous = detail.hazardous,
                closestApproachAu = c.closestApproachAu,
                closestApproachLocal = closestLocal,
                orbit = orbit
            )
        }
    }

    private fun NeoFeedItem.toCandidateOrNull(): NeoCandidate? {
        // pick the first close approach in the feed data (often sorted by time)
        val ca = closeApproachData.firstOrNull() ?: return null
        val au = ca.missDistance.astronomical?.toDoubleOrNull()

        return NeoCandidate(
            id = id,
            name = name,
            hMagnitude = absoluteMagnitudeH,
            isHazardous = hazardous,
            closestApproachAu = au,
            closestApproachUtcIso = ca.closeApproachDateTimeUtc
        )
    }

    private fun parseUtcIsoToLocal(utcIso: String, zoneId: ZoneId): ZonedDateTime {
        // NeoWs close_approach_date_time is ISO8601 in UTC, e.g. "2026-01-12T03:12Z"
        val instant = Instant.parse(utcIso)
        return ZonedDateTime.ofInstant(instant, zoneId)
    }
}
