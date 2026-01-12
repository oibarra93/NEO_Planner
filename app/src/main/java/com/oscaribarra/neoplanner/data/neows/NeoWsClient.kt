package com.oscaribarra.neoplanner.data.neows

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class NeoWsClient(
    private val apiKeyProvider: suspend () -> String
) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }

    suspend fun fetchFeed(startDate: String, endDate: String): NeoFeedResponse {
        val key = apiKeyProvider().ifBlank { error("NeoWs API key is missing. Save it in settings first.") }

        return http.get("https://api.nasa.gov/neo/rest/v1/feed") {
            url {
                parameters.append("start_date", startDate)
                parameters.append("end_date", endDate)
                parameters.append("api_key", key)
            }
            accept(ContentType.Application.Json)
        }.body()
    }

    suspend fun fetchNeoDetail(neoId: String): NeoDetailResponse {
        val key = apiKeyProvider().ifBlank { error("NeoWs API key is missing. Save it in settings first.") }

        return http.get("https://api.nasa.gov/neo/rest/v1/neo/$neoId") {
            url { parameters.append("api_key", key) }
            accept(ContentType.Application.Json)
        }.body()
    }
}
