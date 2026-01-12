package com.oscaribarra.neoplanner.data.geo

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class IpGeoResult(val lat: Double, val lon: Double)

class IpGeoClient(
    private val tokenProvider: suspend () -> String = { "" }
) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun lookup(): IpGeoResult {
        val token = tokenProvider()
        // ipinfo: loc is "lat,lon"
        val resp: IpInfoResponse = http.get("https://ipinfo.io/json") {
            if (token.isNotBlank()) parameter("token", token)
            accept(ContentType.Application.Json)
        }.body()

        val loc = resp.loc?.trim().orEmpty()
        val parts = loc.split(",")
        if (parts.size != 2) error("IP geolocation failed: missing/invalid loc field")
        val lat = parts[0].toDouble()
        val lon = parts[1].toDouble()
        return IpGeoResult(lat, lon)
    }

    @Serializable
    private data class IpInfoResponse(
        val loc: String? = null,
        @SerialName("city") val city: String? = null,
        @SerialName("region") val region: String? = null,
        @SerialName("country") val country: String? = null
    )
}
