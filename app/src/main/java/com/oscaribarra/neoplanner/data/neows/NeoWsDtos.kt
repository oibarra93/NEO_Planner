package com.oscaribarra.neoplanner.data.neows

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NeoFeedResponse(
    @SerialName("near_earth_objects")
    val neosByDate: Map<String, List<NeoFeedItem>> = emptyMap()
)

@Serializable
data class NeoFeedItem(
    val id: String,
    val name: String,
    @SerialName("absolute_magnitude_h")
    val absoluteMagnitudeH: Double? = null,
    @SerialName("is_potentially_hazardous_asteroid")
    val hazardous: Boolean = false,
    @SerialName("close_approach_data")
    val closeApproachData: List<CloseApproachData> = emptyList()
)

@Serializable
data class CloseApproachData(
    @SerialName("close_approach_date_time")
    val closeApproachDateTimeUtc: String,
    @SerialName("miss_distance")
    val missDistance: MissDistance = MissDistance()
)

@Serializable
data class MissDistance(
    val astronomical: String? = null
)

@Serializable
data class NeoDetailResponse(
    val id: String,
    val name: String,
    @SerialName("absolute_magnitude_h")
    val absoluteMagnitudeH: Double? = null,
    @SerialName("is_potentially_hazardous_asteroid")
    val hazardous: Boolean = false,
    @SerialName("orbital_data")
    val orbitalData: OrbitalData
)

@Serializable
data class OrbitalData(
    @SerialName("epoch_osculation")
    val epochJd: String,
    val eccentricity: String,
    @SerialName("semi_major_axis")
    val semiMajorAxisAu: String,
    val inclination: String,
    @SerialName("ascending_node_longitude")
    val ascendingNodeLongitude: String,
    @SerialName("perihelion_argument")
    val perihelionArgument: String,
    @SerialName("mean_anomaly")
    val meanAnomaly: String,
    @SerialName("mean_motion")
    val meanMotionDegPerDay: String? = null
)
