package com.captainavi.app.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSmsRulesTest {
    @Test
    fun `request requires exact phrase but ignores case and edge spaces`() {
        assertTrue(
            OfflineSmsRules.matchesRequest(
                receivedBody = "  captain avi location  ",
                configuredPhrase = "CAPTAIN AVI LOCATION",
            )
        )
        assertFalse(
            OfflineSmsRules.matchesRequest(
                receivedBody = "Please send CAPTAIN AVI LOCATION now",
                configuredPhrase = "CAPTAIN AVI LOCATION",
            )
        )
    }

    @Test
    fun `short request phrase cannot become a trigger`() {
        assertFalse(OfflineSmsRules.matchesRequest("WHERE", "WHERE"))
    }

    @Test
    fun `phone validation supports Maldives local and international numbers`() {
        assertTrue(OfflineSmsRules.isValidPhoneNumber("777-1234"))
        assertTrue(OfflineSmsRules.isValidPhoneNumber("+960 777 1234"))
        assertFalse(OfflineSmsRules.isValidPhoneNumber("123"))
        assertFalse(OfflineSmsRules.isValidPhoneNumber("+1234567890123456"))
    }

    @Test
    fun `normalization preserves explicit international prefix`() {
        assertEquals("+9607771234", OfflineSmsRules.normalizeDialableNumber(" +960 777-1234 "))
        assertEquals("7771234", OfflineSmsRules.normalizeDialableNumber("777-1234"))
    }

    @Test
    fun `automatic reply is limited for five minutes`() {
        val lastReply = 1_000_000L
        assertTrue(OfflineSmsRules.isRateLimited(lastReply, lastReply + 299_999L))
        assertFalse(OfflineSmsRules.isRateLimited(lastReply, lastReply + 300_000L))
    }

    @Test
    fun `location message identifies a stale fix and includes useful details`() {
        val message = OfflineSmsRules.buildLocationMessage(
            captainName = "Ameen",
            position = OfflineSmsPosition(
                latitude = 6.704,
                longitude = 73.123,
                speedKnots = 8.0,
                bearingDegrees = 288f,
                headingCardinal = "WNW",
                accuracyMeters = 7f,
                batteryPct = 82,
                recordedAtMillis = 1_000_000L,
            ),
            nowMillis = 1_000_000L + 12 * 60_000L,
        )

        assertTrue(message.contains("Captain Avi: Ameen"))
        assertTrue(message.contains("6.704000, 73.123000"))
        assertTrue(message.contains("maps.google.com"))
        assertTrue(message.contains("288deg WNW"))
        assertTrue(message.contains("LAST KNOWN 12m ago"))
        assertTrue(message.length <= OfflineSmsRules.MAX_SINGLE_SMS_CHARACTERS)
        assertTrue(message.all { it == '\n' || it.code in 32..126 })
    }

    @Test
    fun `location message stays within one SMS for long values`() {
        val message = OfflineSmsRules.buildLocationMessage(
            captainName = "A very long captain name with Unicode ދިވެހި",
            position = OfflineSmsPosition(
                latitude = -89.123456,
                longitude = -179.123456,
                speedKnots = 99.9,
                bearingDegrees = 359f,
                headingCardinal = "WNW",
                accuracyMeters = 999f,
                batteryPct = 100,
                recordedAtMillis = 1_000_000L,
            ),
            nowMillis = 1_000_000L + 12 * 60_000L,
        )

        assertTrue(message.length <= OfflineSmsRules.MAX_SINGLE_SMS_CHARACTERS)
        assertTrue(message.contains("https://maps.google.com/?q=-89.123456,-179.123456"))
        assertTrue(message.all { it == '\n' || it.code in 32..126 })
    }
}
