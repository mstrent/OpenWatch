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
}

class SkinMinuteBufferTest {

    private val minute = 1_794_100_020_000L - 1_794_100_020_000L % 60_000L

    @Test
    fun `a minute is written once it closes, the mean of points that came one by one`() {
        val buffer = SkinMinuteBuffer()
        assertTrue(buffer.add(listOf(SkinReading(minute + 1_000, 34.0f, Float.NaN, measured = true))).isEmpty())
        assertTrue(buffer.add(listOf(SkinReading(minute + 31_000, 34.2f, Float.NaN, measured = true))).isEmpty())
        val closed = buffer.add(listOf(SkinReading(minute + 61_000, 33.0f, Float.NaN, measured = true)))
        assertEquals(listOf(WearLinkProtocol.SkinMinute(minute, 3410, SkinTemperatureRecorder.PLACEHOLDER_AMBIENT100)), closed)
    }

    @Test
    fun `flush writes the open minute and empties the buffer`() {
        val buffer = SkinMinuteBuffer()
        buffer.add(listOf(SkinReading(minute + 1_000, 34.0f, Float.NaN, measured = true)))
        assertEquals(listOf(minute), buffer.flush().map { it.epochMillis })
        assertTrue(buffer.flush().isEmpty())
    }

    @Test
    fun `a batch of whole minutes closes all but its newest`() {
        val buffer = SkinMinuteBuffer()
        val closed = buffer.add(
            listOf(
                SkinReading(minute + 1_000, 34.0f, 22.0f, measured = true),
                SkinReading(minute + 61_000, 34.1f, 22.0f, measured = true),
                SkinReading(minute + 121_000, 34.2f, 22.0f, measured = true),
            ),
        )
        assertEquals(listOf(minute, minute + 60_000), closed.map { it.epochMillis })
    }
}

class SkinHoldWindowsTest {

    private val t = 1_794_100_020_000L - 1_794_100_020_000L % 60_000L

    @Test
    fun `a minute inside a hold is held, one before or after it is not`() {
        val windows = SkinHoldWindows()
        windows.onHoldChange(holding = true, atMillis = t + 10 * 60_000)
        windows.onHoldChange(holding = false, atMillis = t + 20 * 60_000)
        assertEquals(false, windows.isHeld(t + 9 * 60_000))
        assertEquals(true, windows.isHeld(t + 10 * 60_000))
        assertEquals(true, windows.isHeld(t + 19 * 60_000))
        assertEquals(false, windows.isHeld(t + 20 * 60_000))
    }

    @Test
    fun `an open hold holds every minute from its start`() {
        val windows = SkinHoldWindows()
        windows.onHoldChange(holding = true, atMillis = t)
        assertEquals(true, windows.isHeld(t + 600 * 60_000))
        assertEquals(false, windows.isHeld(t - 60_000))
    }

    @Test
    fun `a night-only source writes the night series whatever the hold`() {
        val windows = SkinHoldWindows()
        assertEquals(WearMetrics.SKIN_TEMPERATURE, SkinTemperatureRecorder.metricFor(t, nightOnly = true, windows))
        assertEquals(WearMetrics.SKIN_TEMPERATURE_DAY, SkinTemperatureRecorder.metricFor(t, nightOnly = false, windows))
        windows.onHoldChange(holding = true, atMillis = t)
        assertEquals(WearMetrics.SKIN_TEMPERATURE, SkinTemperatureRecorder.metricFor(t, nightOnly = false, windows))
    }
}
