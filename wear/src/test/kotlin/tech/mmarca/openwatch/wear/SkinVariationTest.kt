package tech.mmarca.openwatch.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.mmarca.openwatch.wearlink.WearLinkProtocol.SkinMinute
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SkinVariationTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 11, 27)

    /** [minutes] minutes at [celsius] from [start] on [date]'s clock. */
    private fun run(date: LocalDate, start: LocalTime, minutes: Int, celsius: Double): List<SkinMinute> {
        val first = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        return (0 until minutes).map { SkinMinute(first + it * 60_000L, (celsius * 100).toInt(), 2300) }
    }

    /** A plain night ending on [date]: 01:00 to 07:00 that morning. */
    private fun night(date: LocalDate, celsius: Double) = run(date, LocalTime.of(1, 0), 360, celsius)

    @Test
    fun `no night, no reading`() {
        assertEquals(SkinVariation.Status.NoNight, SkinVariation.status(emptyList(), today))
        val olderOnly = SkinVariation.nights(night(today.minusDays(2), 33.0), zone)
        assertEquals(SkinVariation.Status.NoNight, SkinVariation.status(olderOnly, today))
    }

    @Test
    fun `a night spanning midnight is one night, named by the morning it ends`() {
        val rows = run(today.minusDays(1), LocalTime.of(23, 0), 120, 33.5)
        val nights = SkinVariation.nights(rows, zone)
        assertEquals(listOf(SkinVariation.Night(today, 33.5)), nights)
    }

    @Test
    fun `a run under thirty minutes is not a night`() {
        assertTrue(SkinVariation.nights(run(today, LocalTime.of(3, 0), 29, 33.0), zone).isEmpty())
    }

    @Test
    fun `before three earlier nights the baseline is still being built`() {
        val rows = night(today, 34.0) + night(today.minusDays(1), 33.0) + night(today.minusDays(2), 33.0)
        assertEquals(SkinVariation.Status.Building(nightsNeeded = 1), SkinVariation.status(SkinVariation.nights(rows, zone), today))
    }

    @Test
    fun `last night against the median of the earlier nights`() {
        val rows = night(today, 34.0) + night(today.minusDays(1), 33.0) + night(today.minusDays(2), 33.4) + night(today.minusDays(3), 33.2)
        val status = SkinVariation.status(SkinVariation.nights(rows, zone), today) as SkinVariation.Status.Delta
        assertEquals(0.8, status.celsius, 1e-9)
        assertEquals(7, status.week.size)
        assertEquals(today, status.week.last().first)
        assertEquals(0.8, status.week.last().second!!, 1e-9)
        assertNull("no baseline yet that night", status.week[5].second)
    }

    @Test
    fun `the baseline remembers thirty nights`() {
        // 31 earlier nights: the oldest, far warmer, falls out of the baseline.
        val earlier = (1..30).flatMap { night(today.minusDays(it.toLong()), 33.0) } + night(today.minusDays(31), 40.0)
        val status = SkinVariation.status(SkinVariation.nights(night(today, 33.0) + earlier, zone), today) as SkinVariation.Status.Delta
        assertEquals(0.0, status.celsius, 1e-9)
    }

    @Test
    fun `deltas are signed, one decimal, Fahrenheit without the offset`() {
        assertEquals("+0.3°", SkinVariation.formatDelta(0.25, UnitSystem.METRIC))
        assertEquals("−0.1°", SkinVariation.formatDelta(-0.1, UnitSystem.METRIC))
        assertEquals("0.0°", SkinVariation.formatDelta(0.01, UnitSystem.METRIC))
        assertEquals("+0.2°", SkinVariation.formatDelta(0.1, UnitSystem.IMPERIAL))
    }
}
