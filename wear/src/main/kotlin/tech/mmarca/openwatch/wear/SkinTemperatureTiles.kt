package tech.mmarca.openwatch.wear

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/*
 * Skin temperature as Google Health shows it on the wrist: last night's variation from the
 * wearer's own baseline ([SkinVariation]), as a tile with the last week's nights and as a
 * complication. Both read only the held nights ([WearMetrics.SKIN_TEMPERATURE]); the
 * watch-only daytime minutes are the dashboard's "Skin temp now" alone.
 */

/** Rows the month of nights can hold: a night's minutes are some 500. */
private const val SKIN_MONTH_ROWS = 40_000

/** The variation now, from the store; blocks on it. */
internal fun readSkinVariation(store: MetricStore, nowMillis: Long, zone: ZoneId): SkinVariation.Status {
    val since = nowMillis - WearMetrics.SKIN_TEMPERATURE.retentionMillis
    val nights = SkinVariation.nights(store.since(WearMetrics.SKIN_TEMPERATURE, since, SKIN_MONTH_ROWS), zone)
    return SkinVariation.status(nights, Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
}

/** The figure both show: the signed variation, or a dash and why there is none. */
internal fun Context.skinVariationFigure(status: SkinVariation.Status): TileFigure = when (status) {
    SkinVariation.Status.NoNight -> getString(R.string.tile_skin_no_night).let { TileFigure(TILE_NO_VALUE, it, it) }
    is SkinVariation.Status.Building -> resources.getQuantityString(R.plurals.tile_skin_building, status.nightsNeeded, status.nightsNeeded)
        .let { TileFigure(TILE_NO_VALUE, it, it) }
    is SkinVariation.Status.Delta -> {
        val value = SkinVariation.formatDelta(status.celsius, WearSettings.unitSystem(this))
        val caption = getString(R.string.tile_skin_caption)
        TileFigure(value, caption, getString(R.string.tile_skin_spoken, value))
    }
}

/** Last night's variation, and the week's nights as bars around the baseline. */
class SkinTemperatureTileService : MetricTileService() {

    /** `SkinTemperatureColor`. */
    override val accent: Int = 0xFFD98A4E.toInt()

    override val titleRes: Int = R.string.tile_skin_label

    override fun read(day: TileDay, chart: TileChart, store: MetricStore): MetricTileContent {
        val status = readSkinVariation(store, day.nowMillis, ZoneId.systemDefault())
        val figure = skinVariationFigure(status)
        val title = getString(titleRes)
        val week = (status as? SkinVariation.Status.Delta)?.let { delta -> chart.deltas(delta.week.map { it.second }) }
        return MetricTileContent(
            title = title,
            value = figure.value,
            caption = figure.caption,
            accent = accent,
            description = "$title: ${figure.spoken}",
            chart = week,
            detail = week?.let { getString(R.string.tile_skin_week) },
        )
    }
}

/** Last night's variation as a short text: "+0.3°", or a dash until there is one. */
class SkinTemperatureComplicationService : MetricComplicationService() {

    override val iconRes: Int = R.drawable.ic_metric_skin_temperature

    override val unitRes: Int = R.string.complication_skin_title

    override fun read(store: MetricStore, nowMillis: Long): Reading =
        skinVariationFigure(readSkinVariation(store, nowMillis, ZoneId.systemDefault())).let { Reading(it.value, it.spoken) }

    override fun preview(): Reading =
        SkinVariation.formatDelta(PREVIEW_DELTA, WearSettings.unitSystem(this)).let { Reading(it, getString(R.string.tile_skin_spoken, it)) }

    private companion object {
        const val PREVIEW_DELTA = 0.3
    }
}
