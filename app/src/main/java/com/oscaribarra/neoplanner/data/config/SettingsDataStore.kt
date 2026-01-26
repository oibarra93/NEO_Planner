package com.oscaribarra.neoplanner.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val NEOWS_API_KEY = stringPreferencesKey("neows_api_key")
        val IPINFO_TOKEN = stringPreferencesKey("ipinfo_token") // optional

        val AZ_OFFSET_DEG = stringPreferencesKey("camera_az_offset_deg")
        val ALT_OFFSET_DEG = stringPreferencesKey("camera_alt_offset_deg")
    }

    val neoWsApiKeyFlow: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[Keys.NEOWS_API_KEY].orEmpty() }

    val ipInfoTokenFlow: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[Keys.IPINFO_TOKEN].orEmpty() }

    suspend fun setNeoWsApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[Keys.NEOWS_API_KEY] = key.trim() }
    }

    suspend fun setIpInfoToken(token: String) {
        context.dataStore.edit { prefs -> prefs[Keys.IPINFO_TOKEN] = token.trim() }
    }

    suspend fun getNeoWsApiKey(): String {
        return context.dataStore.data
            .map { it[Keys.NEOWS_API_KEY].orEmpty() }
            .first()
    }

    suspend fun getIpInfoToken(): String {
        return context.dataStore.data
            .map { it[Keys.IPINFO_TOKEN].orEmpty() }
            .first()
    }

    suspend fun setAzOffsetDeg(value: Double) {
        context.dataStore.edit { prefs -> prefs[Keys.AZ_OFFSET_DEG] = value.toString() }
    }

    suspend fun setAltOffsetDeg(value: Double) {
        context.dataStore.edit { prefs -> prefs[Keys.ALT_OFFSET_DEG] = value.toString() }
    }

    suspend fun getAzOffsetDeg(): Double =
        context.dataStore.data.map { it[Keys.AZ_OFFSET_DEG]?.toDoubleOrNull() ?: 0.0 }.first()

    suspend fun getAltOffsetDeg(): Double =
        context.dataStore.data.map { it[Keys.ALT_OFFSET_DEG]?.toDoubleOrNull() ?: 0.0 }.first()



}