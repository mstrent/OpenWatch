package tech.mmarca.openwatch.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.mmarca.openwatch.wearlink.WearLinkProtocol

class SkinTemperatureRecorderTest {

    private val minute = 1_794_100_020_000L - 1_794_100_020_000L % 60_000L

    @Test
    fun `good points become one row per minute, the mean of each`() {
        val rows = SkinTemperatureRecorder.minutes(
            listOf(
                SkinReading(minute + 1_000, 34.0f, 22.0f, measured = true),
                SkinReading(minute + 31_000, 34.2f, 22.4f, measured = true),
                SkinReading(minute + 61_000, 33.9f, 22.1f, measured = true),
            ),
        )
        assertEquals(
            listOf(WearLinkProtocol.SkinMinute(minute, 3410, 2220), WearLinkProtocol.SkinMinute(minute + 60_000, 3390, 2210)),
            rows,
        )
    }

    @Test
    fun `a flagged, missing or impossible point is left out`() {
        val rows = SkinTemperatureRecorder.minutes(
            listOf(
                SkinReading(minute, 34.0f, 22.0f, measured = false),
                SkinReading(minute, Float.NaN, 22.0f, measured = true),
                SkinReading(minute, 12.0f, 22.0f, measured = true),
            ),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a skin-only minute, the Pixel channel, stamps the placeholder ambient and still round-trips`() {
        val rows = SkinTemperatureRecorder.minutes(
            listOf(
                SkinReading(minute + 1_000, 34.0f, Float.NaN, measured = true),
                SkinReading(minute + 31_000, 34.2f, Float.NaN, measured = true),
            ),
        )
        assertEquals(listOf(WearLinkProtocol.SkinMinute(minute, 3410, SkinTemperatureRecorder.PLACEHOLDER_AMBIENT100)), rows)
        // The wire line parses back to the same row: the placeholder keeps it in bounds.
        val line = WearLinkProtocol.formatSkinMinute(rows.single())
        assertEquals(rows.single(), WearLinkProtocol.parseSkinMinute(line))
    }

    @Test
    fun `a mixed minute averages only the real ambients, the missing one says nothing`() {
        val rows = SkinTemperatureRecorder.minutes(
            listOf(
                SkinReading(minute + 1_000, 34.0f, 22.0f, measured = true),
                SkinReading(minute + 31_000, 34.2f, Float.NaN, measured = true),
                SkinReading(minute + 41_000, 34.4f, 22.4f, measured = true),
            ),
        )
        assertEquals(listOf(WearLinkProtocol.SkinMinute(minute, 3420, 2220)), rows)
    }

    @Test
    fun `a held night's minutes sync, the day's stay on the watch`() {
        assertEquals(WearMetrics.SKIN_TEMPERATURE, SkinTemperatureRecorder.metricFor(sleepHolding = true))
        assertEquals(WearMetrics.SKIN_TEMPERATURE_DAY, SkinTemperatureRecorder.metricFor(sleepHolding = false))
    }
}
