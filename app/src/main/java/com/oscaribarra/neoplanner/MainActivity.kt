package com.oscaribarra.neoplanner

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.oscaribarra.neoplanner.data.config.SettingsDataStore
import com.oscaribarra.neoplanner.data.geo.IpGeoClient
import com.oscaribarra.neoplanner.data.geo.ObserverProvider
import com.oscaribarra.neoplanner.data.neows.NeoWsClient
import com.oscaribarra.neoplanner.data.repo.NeoRepository
import com.oscaribarra.neoplanner.ui.NeoPlannerScreen
import com.oscaribarra.neoplanner.ui.NeoPlannerViewModel
import com.oscaribarra.neoplanner.ui.NeoPlannerViewModelFactory
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
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

        // Optional debug: run fetch and then run NEO Alt/Az once results exist.
        // Enable this after your API key Save flow is confirmed.

        lifecycleScope.launch {
            vm.fetchNeosNow()

            vm.state
                .filter { it.results.isNotEmpty() && it.observer != null }
                .collectLatest {
                    vm.debugFirstNeoAltAz(applicationContext)
                    this.cancel()
                }
        }


        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NeoPlannerScreen(vm)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional cleanup if you want:
        // ipGeo.close()
        // neoWsClient.close()
    }
}
