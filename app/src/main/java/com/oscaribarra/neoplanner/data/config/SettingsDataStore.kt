package com.oscaribarra.neoplanner.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        // quick sync-style getter (still suspend)
        var value = ""
        context.dataStore.data.map { it[Keys.NEOWS_API_KEY].orEmpty() }.collectOnce { value = it }
        return value
    }

    suspend fun getIpInfoToken(): String {
        var value = ""
        context.dataStore.data.map { it[Keys.IPINFO_TOKEN].orEmpty() }.collectOnce { value = it }
        return value
    }
}

private suspend inline fun <T> Flow<T>.collectOnce(crossinline block: (T) -> Unit) {
    // simple helper to avoid bringing in first() import everywhere
    var done = false
    collect { v ->
        if (!done) {
            done = true
            block(v)
            return@collect
        }
    }
}
