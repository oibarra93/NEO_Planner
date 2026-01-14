package com.oscaribarra.neoplanner.data.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.oscaribarra.neoplanner.data.model.Observer
import java.time.ZoneId

class ObserverProvider(
    private val context: Context,
    private val ipGeoClient: IpGeoClient
) {
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getObserver(): Observer {
        val tz = ZoneId.systemDefault().id

        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val loc = fused.lastLocationOrNull(context)
            if (loc != null) {
                val elev = if (loc.hasAltitude()) loc.altitude else 0.0
                return Observer(
                    latitudeDeg = loc.latitude,
                    longitudeDeg = loc.longitude,
                    elevationMeters = elev,
                    timeZoneId = tz
                )
            }
        }

        val ip = ipGeoClient.lookup()
        return Observer(
            latitudeDeg = ip.lat,
            longitudeDeg = ip.lon,
            elevationMeters = 0.0,
            timeZoneId = tz
        )
    }
}
