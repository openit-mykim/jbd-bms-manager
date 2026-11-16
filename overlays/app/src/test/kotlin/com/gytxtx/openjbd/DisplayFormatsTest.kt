package com.gytxtx.openjbd

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayFormatsTest {
    private val instant = 1_763_300_000_000L // 2025-11-16T13:33:20Z

    @Test
    fun usesTheDeviceTimeZoneNotAFixedOne() {
        val utc = DisplayFormats.dateTime(instant, Locale.US, TimeZone.getTimeZone("UTC"))
        val seoul = DisplayFormats.dateTime(instant, Locale.US, TimeZone.getTimeZone("Asia/Seoul"))
        assertTrue(utc, utc.contains("1:33:20"))
        assertTrue(seoul, seoul.contains("10:33:20"))
    }

    @Test
    fun followsTheLocaleFormat() {
        val zone = TimeZone.getTimeZone("UTC")
        assertNotEquals(
            DisplayFormats.dateTime(instant, Locale.US, zone),
            DisplayFormats.dateTime(instant, Locale.KOREA, zone)
        )
    }
}
