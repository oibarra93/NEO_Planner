package com.oscaribarra.neoplanner.data.model

import java.time.ZonedDateTime

data class Observer(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val elevationMeters: Double,
    val timeZoneId: String
)

data class NeoCandidate(
    val id: String,
    val name: String,
    val hMagnitude: Double?,
    val isHazardous: Boolean,
    val closestApproachAu: Double?,
    val closestApproachUtcIso: String?, // keep raw UTC for now
)

data class NeoWithOrbit(
    val id: String,
    val name: String,
    val hMagnitude: Double?,
    val isHazardous: Boolean,
    val closestApproachAu: Double?,
    val closestApproachLocal: ZonedDateTime?,
    val orbit: OrbitElements
)

data class OrbitElements(
    val epochJd: Double,
    val e: Double,
    val aAu: Double,
    val iDeg: Double,
    val raanDeg: Double,
    val argPeriDeg: Double,
    val meanAnomDeg: Double,
    val meanMotionDegPerDay: Double?
)
