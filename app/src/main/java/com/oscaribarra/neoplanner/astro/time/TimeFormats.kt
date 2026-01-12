package com.oscaribarra.neoplanner.astro.time

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeFormats {
    @RequiresApi(Build.VERSION_CODES.O)
    val localDateTimeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    @RequiresApi(Build.VERSION_CODES.O)
    val localDateTimeWithZoneFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")

    @RequiresApi(Build.VERSION_CODES.O)
    fun formatLocal(zdt: ZonedDateTime): String = zdt.format(localDateTimeFmt)
    @RequiresApi(Build.VERSION_CODES.O)
    fun formatLocalWithZone(zdt: ZonedDateTime): String = zdt.format(localDateTimeWithZoneFmt)
}
