package com.oscaribarra.neoplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.oscaribarra.neoplanner.data.config.SettingsDataStore
import com.oscaribarra.neoplanner.data.geo.ObserverProvider
import com.oscaribarra.neoplanner.data.repo.NeoRepository

class NeoPlannerViewModelFactory(
    private val settings: SettingsDataStore,
    private val observerProvider: ObserverProvider,
    private val repo: NeoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NeoPlannerViewModel(settings, observerProvider, repo) as T
    }
}
