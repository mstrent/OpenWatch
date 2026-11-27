package tech.mmarca.openwatch.wear

import tech.mmarca.healthcalculations.stats.medianOrNull
import tech.mmarca.openwatch.wearlink.WearLinkProtocol.SkinMinute
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Last night's skin temperature as Google Health shows it: the variation from the
 * wearer's own baseline. A night is the minutes of one [WearReadings.nightWindow] (noon to
 * noon, named by the morning it ends), its value their median; the baseline is the
 * median of up to [MAX_BASELINE_NIGHTS] earlier nights' values, once there are
 * [MIN_BASELINE_NIGHTS]. The numbers are the phone's (`WearOsSkinTemperatureImport`), so
 * the watch and Health Connect agree. Pure, so it is tested off the watch.
 */
internal object SkinVariation {

    const val MIN_BASELINE_NIGHTS = 3
    const val MAX_BASELINE_NIGHTS = 30

    /** Fewer minutes than this in a night is a nap or a test, not a night's temperature. */
    const val MIN_NIGHT_MINUTES = 30

    /** The nights the tile's chart shows, last night last. */
    const val WEEK_NIGHTS = 7

    data class Night(val date: LocalDate, val medianCelsius: Double)

    sealed interface Status {
        /** No night ended this morning. */
        data object NoNight : Status

        /** Last night is there, but [nightsNeeded] more earlier nights are needed for a baseline. */
        data class Building(val nightsNeeded: Int) : Status

        /** Last night against the baseline, and the last [WEEK_NIGHTS] nights' variations (null where none), oldest first. */
        data class Delta(val celsius: Double, val week: List<Pair<LocalDate, Double?>>) : Status
    }

    /** The nights in [rows] (the synced night series), oldest first. */
    fun nights(rows: List<SkinMinute>, zone: ZoneId): List<Night> =
        rows.groupBy { nightOf(it.epochMillis, zone) }
            .filterValues { it.size >= MIN_NIGHT_MINUTES }
            .mapNotNull { (date, minutes) -> minutes.map { it.skinCelsius }.medianOrNull()?.let { Night(date, it) } }
            .sortedBy { it.date }

    fun status(nights: List<Night>, today: LocalDate): Status {
        val byDate = nights.associate { it.date to it.medianCelsius }
        val lastNight = byDate[today] ?: return Status.NoNight
        val earlier = baseline(nights, today)
        if (earlier == null) {
            val have = nights.count { it.date < today }.coerceAtMost(MIN_BASELINE_NIGHTS)
            return Status.Building(MIN_BASELINE_NIGHTS - have)
        }
        val week = (WEEK_NIGHTS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            date to byDate[date]?.let { value -> baseline(nights, date)?.let { value - it } }
        }
        return Status.Delta(lastNight - earlier, week)
    }

    /** "+0.3°", "−0.1°", "0.0°": one decimal, signed, a Fahrenheit difference without the 32. */
    fun formatDelta(celsius: Double, unitSystem: UnitSystem): String {
        val value = if (unitSystem == UnitSystem.IMPERIAL) celsius * FAHRENHEIT_PER_CELSIUS else celsius
        val tenths = (value * 10).roundToInt()
        val sign = when {
            tenths > 0 -> "+"
            tenths < 0 -> "−"
            else -> ""
        }
        return sign + String.format(Locale.getDefault(), "%.1f", abs(tenths) / 10.0) + "°"
    }

    /** The median of the up-to-30 nights before [date], or null with fewer than three. */
    private fun baseline(nights: List<Night>, date: LocalDate): Double? =
        nights.filter { it.date < date }
            .takeLast(MAX_BASELINE_NIGHTS)
            .takeIf { it.size >= MIN_BASELINE_NIGHTS }
            ?.map { it.medianCelsius }
            ?.medianOrNull()

    /** The night a minute belongs to: noon to noon, named by the date it ends on. */
    private fun nightOf(epochMillis: Long, zone: ZoneId): LocalDate {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return if (local.toLocalTime() < LocalTime.NOON) local.toLocalDate() else local.toLocalDate().plusDays(1)
    }

    private const val FAHRENHEIT_PER_CELSIUS = 1.8
}
