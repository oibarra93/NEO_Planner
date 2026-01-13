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

}