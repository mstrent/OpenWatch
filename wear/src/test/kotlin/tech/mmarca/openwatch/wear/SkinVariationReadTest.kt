package tech.mmarca.openwatch.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import tech.mmarca.openwatch.wearlink.WearLinkProtocol.SkinMinute
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SkinVariationReadTest {

    private val store = MetricStore(ApplicationProvider.getApplicationContext(), name = null)
    private val today = LocalDate.of(2026, 11, 27)
    private val now = today.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @After
    fun close() = store.close()

    /** An hour of minutes at [celsius] from 02:00 on [date]. */
    private fun night(date: LocalDate, celsius: Double): List<SkinMinute> {
        val start = date.atTime(2, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return (0 until 60).map { SkinMinute(start + it * 60_000L, (celsius * 100).toInt(), 2300) }
    }

    @Test
    fun `the tile reads last night against the held nights of the month, never the day's minutes`() {
        store.putAll(WearMetrics.SKIN_TEMPERATURE, night(today, 33.6) + (1..3).flatMap { night(today.minusDays(it.toLong()), 33.2) })
        // A warm afternoon on the watch-only series must not move the baseline.
        store.putAll(WearMetrics.SKIN_TEMPERATURE_DAY, night(today.minusDays(1), 36.0))

        val status = readSkinVariation(store, now, ZoneOffset.UTC) as SkinVariation.Status.Delta

        assertEquals(0.4, status.celsius, 1e-9)
    }

    @Test
    fun `two earlier nights still build the baseline`() {
        store.putAll(WearMetrics.SKIN_TEMPERATURE, night(today, 33.6) + (1..2).flatMap { night(today.minusDays(it.toLong()), 33.2) })

        assertEquals(SkinVariation.Status.Building(1), readSkinVariation(store, now, ZoneOffset.UTC))
    }
}
