package com.oscaribarra.neoplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import com.oscaribarra.neoplanner.data.config.SettingsDataStore
import com.oscaribarra.neoplanner.data.geo.IpGeoClient
import com.oscaribarra.neoplanner.data.geo.ObserverProvider
import com.oscaribarra.neoplanner.data.neows.NeoWsClient
import com.oscaribarra.neoplanner.data.repo.NeoRepository
import com.oscaribarra.neoplanner.ui.NeoPlannerScreen
import com.oscaribarra.neoplanner.ui.NeoPlannerViewModel
import com.oscaribarra.neoplanner.ui.NeoPlannerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = SettingsDataStore(applicationContext)
        val ipGeo = IpGeoClient(tokenProvider = { settings.getIpInfoToken() })
        val observerProvider = ObserverProvider(applicationContext, ipGeo)
        val neoWsClient = NeoWsClient(apiKeyProvider = { settings.getNeoWsApiKey() })
        val repo = NeoRepository(neoWsClient)

        val factory = NeoPlannerViewModelFactory(settings, observerProvider, repo)
        val vm: NeoPlannerViewModel =
            ViewModelProvider(this, factory)[NeoPlannerViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NeoPlannerScreen(vm)
                }
            }
        }
    }
}
