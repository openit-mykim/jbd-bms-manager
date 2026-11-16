package com.gytxtx.openjbd

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single place for user-visible date/time text (mobile dev standard 5.8: locale formats, no
 * hard-coded pattern or time zone). Machine formats such as the backup file's UTC stamp stay
 * where they are defined.
 */
object DisplayFormats {
    fun dateTime(
        millis: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
        .apply { this.timeZone = timeZone }
        .format(Date(millis))
}
