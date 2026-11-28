package tech.mmarca.openwatch.wear

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlin.math.roundToInt
import tech.mmarca.openwatch.wearlink.WearLinkProtocol

/**
 * Skin temperature at night, one [WearMetrics.SKIN_TEMPERATURE] row per
 * clock minute: the mean of the minute's good points, skin and ambient. The
 * points come from a [SpotSource]: the raw Pixel sensor on a Pixel Watch,
 * the Samsung Health Platform's tracker elsewhere ([WearGraph] chooses).
 * A watch with neither has no skin temperature.
 *
 * Runs only while sleep is held and the wearer switched it on
 * ([WearSettings.skinTemperature]). In the bench test on a Galaxy Watch7 the
 * tracker delivered nothing; the debug protocol counts what arrives
 * (`samsung.skinPoints`, `skin.*`).
 */
internal class SkinTemperatureRecorder(
    private val store: MetricStore,
    private val source: SpotSource<SkinReading>,
    private val debug: DebugProtocol,
    /** A source that records only in a sleep hold (Samsung's): every minute is a night's. */
    private val nightOnly: Boolean = true,
    /** When sleep was held, so each minute goes to the series of its own time ([metricFor]). */
    private val holds: SkinHoldWindows = SkinHoldWindows(),
) : Recorder, SpotSource.Sink<SkinReading> {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    /** Read and written on the worker thread only. */
    private val buffer = SkinMinuteBuffer()

    val isAvailable: Boolean
        get() = source.isAvailable

    override val isRunning: Boolean
        get() = thread != null

    override fun apply(plan: RecorderPlan.Plan) = setEnabled(plan.skinTemperature)

    /** Starts or stops the recorder. Main thread. */
    fun setEnabled(enabled: Boolean) {
        if (enabled && source.isAvailable) start() else stop()
    }

    private fun start() {
        if (thread != null) return
        val worker = HandlerThread("SkinTemperatureRecorder").apply { start() }
        val workerHandler = Handler(worker.looper)
        if (!source.start(workerHandler, this)) {
            worker.quitSafely()
            return
        }
        thread = worker
        handler = workerHandler
        Log.i(TAG, "Skin temperature on")
    }

    override fun stop() {
        val worker = thread ?: return
        source.stop()
        // The open minute is written before the thread ends: quitSafely runs what is posted first.
        handler?.post { write(buffer.flush()) }
        worker.quitSafely()
        thread = null
        handler = null
        Log.i(TAG, "Skin temperature off")
    }

    override fun onError() {
        debug.event("Skin", "tracker_error")
    }

    override fun onReadings(readings: List<SkinReading>) {
        debug.count("skin.points", readings.size.toLong())
        write(buffer.add(readings))
    }

    /** Each closed minute once, into the series its own time belongs to. */
    private fun write(rows: List<WearLinkProtocol.SkinMinute>) {
        if (rows.isEmpty()) return
        debug.count("skin.rows", rows.size.toLong())
        val now = System.currentTimeMillis()
        for ((metric, inMetric) in rows.groupBy { metricFor(it.epochMillis, nightOnly, holds) }) {
            store.putAll(metric, inMetric)
            store.prune(metric, now)
        }
    }

    companion object {
        private const val TAG = "SkinTemperatureRecorder"
        private const val MINUTE_MILLIS = 60_000L

        /**
         * What a minute with no ambient at all stamps into the row. The wire
         * line demands an ambient value in range, and the raw Pixel skin
         * channel is skin-only; the placeholder is for the round-trip check
         * and the phone's parser only — [tech.mmarca.openwatch.devices.wearos.WearSeries]
         * maps just the skin Celsius into Health Connect, so the phone side
         * discards ambient and this number never reaches it. Mid-range keeps
         * it inside the protocol's bounds whatever a future reader assumes.
         */
        internal const val PLACEHOLDER_AMBIENT100 = 2300

        /**
         * Where the minute starting at [minuteStart] goes: the synced night series when a sleep
         * hold covered it, or always for a [nightOnly] source; the watch-only day series otherwise.
         * By the minute's own time, not the batch's: points wait in the sensor's FIFO and a batch
         * can land after the hold started or ended.
         */
        internal fun metricFor(minuteStart: Long, nightOnly: Boolean, holds: SkinHoldWindows) =
            if (nightOnly || holds.isHeld(minuteStart)) WearMetrics.SKIN_TEMPERATURE else WearMetrics.SKIN_TEMPERATURE_DAY

        /** The minutes the good points fall in, each the mean of its points. Pure. */
        internal fun minutes(points: List<SkinReading>): List<WearLinkProtocol.SkinMinute> =
            points.filter { it.measured && !it.skinCelsius.isNaN() }
                .groupBy { it.epochMillis - it.epochMillis % MINUTE_MILLIS }
                .mapNotNull { (minute, inMinute) ->
                    val skin = (inMinute.map { it.skinCelsius }.average() * 100).roundToInt()
                    // Average only the minutes' real ambients; a skin-only channel says nothing about ambient.
                    val ambients = inMinute.map { it.ambientCelsius }.filter { !it.isNaN() }
                    val ambient = if (ambients.isEmpty()) PLACEHOLDER_AMBIENT100 else (ambients.average() * 100).roundToInt()
                    val row = WearLinkProtocol.SkinMinute(minute, skin, ambient)
                    row.takeIf { WearLinkProtocol.parseSkinMinute(WearLinkProtocol.formatSkinMinute(it)) != null }
                }
                .sortedBy { it.epochMillis }
    }
}

/**
 * Collects skin points until their minute closes, so a minute is written once, as the mean of all
 * its points, however the source hands them over: the Pixel sensor one at a time, the Samsung
 * tracker in batches. A minute closes when a point of a later minute arrives, or at [flush].
 * Not thread-safe: the recorder's worker thread owns it.
 */
internal class SkinMinuteBuffer {

    private val pending = ArrayList<SkinReading>()

    /** Adds [points] and returns the minutes they closed, oldest first. */
    fun add(points: List<SkinReading>): List<WearLinkProtocol.SkinMinute> {
        pending += points
        val newest = pending.maxOfOrNull { minuteOf(it.epochMillis) } ?: return emptyList()
        val (closed, open) = pending.partition { minuteOf(it.epochMillis) < newest }
        pending.clear()
        pending += open
        return SkinTemperatureRecorder.minutes(closed)
    }

    /** Every minute still open, then empty. */
    fun flush(): List<WearLinkProtocol.SkinMinute> = SkinTemperatureRecorder.minutes(pending.toList()).also { pending.clear() }

    private fun minuteOf(epochMillis: Long) = epochMillis - epochMillis % MINUTE_MILLIS

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

/**
 * When sleep was held: the starts and ends the sleep recorder reports, the newest [KEEP] kept.
 * Thread-safe: the sleep recorder's thread writes, the skin recorder's reads.
 */
internal class SkinHoldWindows {

    /** Start and end (Long.MAX_VALUE while open) of each hold, oldest first. */
    private val windows = ArrayDeque<LongArray>()

    @Synchronized
    fun onHoldChange(holding: Boolean, atMillis: Long) {
        val open = windows.lastOrNull()?.takeIf { it[1] == Long.MAX_VALUE }
        if (holding) {
            if (open != null) return
            windows.addLast(longArrayOf(atMillis, Long.MAX_VALUE))
            while (windows.size > KEEP) windows.removeFirst()
        } else {
            open?.set(1, atMillis)
        }
    }

    /** Whether a hold covered [epochMillis]. */
    @Synchronized
    fun isHeld(epochMillis: Long): Boolean = windows.any { epochMillis >= it[0] && epochMillis < it[1] }

    private companion object {
        /** More holds than two days bring, the longest a minute waits in a FIFO or a buffer. */
        const val KEEP = 16
    }
}
