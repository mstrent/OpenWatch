package tech.mmarca.openwatch.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

/**
 * Skin temperature on the Pixel Watch: the raw vendor sensor
 * `com.google.sensor.skin_temperature`, found through the sensor list by its
 * string type (the way the PPG spike in [PpgRawLogger] opens Samsung's — the
 * SDK has no `getDefaultSensor(String)` overload) behind the
 * `health.READ_SKIN_TEMPERATURE` grant the manifest already declares. Every
 * other watch — every Samsung one, where that sensor is signature-gated — has
 * no such sensor, reports itself unavailable through [isAvailable], and
 * [WearGraph] keeps the Samsung platform's tracker.
 *
 * The sensor delivers on-change, real °C roughly every 30 s while worn, and
 * carries a single channel: skin, no ambient, so [SkinReading.ambientCelsius]
 * is NaN for every point. [SkinTemperatureRecorder] knows the channel is
 * absent and stamps a placeholder ambient only for the wire. Non-wake-up and
 * unbatched: recording all day, it must not wake the processor; its points
 * wait in the FIFO for the next wake-up and keep their own times.
 */
internal class SensorSkinTemperatureSource(
    private val context: Context,
    private val debug: DebugProtocol,
) : SpotSource<SkinReading>, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Set by [start], cleared by [stop]. */
    private var sink: SpotSource.Sink<SkinReading>? = null

    /** When [start] registered the listener. */
    private var registeredAtMillis = 0L

    override val isAvailable: Boolean
        get() = skinSensor() != null

    override fun start(handler: Handler, sink: SpotSource.Sink<SkinReading>): Boolean {
        val found = skinSensor() ?: return false
        this.sink = sink
        registeredAtMillis = System.currentTimeMillis()
        val registered = try {
            sensorManager.registerListener(this, found, SAMPLING, 0, handler)
                .also { if (it) debug.registered("skinTemperature", found, SAMPLING, 0) }
        } catch (e: SecurityException) {
            // The grant was revoked between the availability check and here.
            debug.event("Skin", "source_refused", "error" to e)
            false
        }
        if (!registered) {
            this.sink = null
            sink.onError()
        }
        return registered
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        sink = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        // By string type, not the numeric constant: the vendor sensor is not
        // the `android.sensor.skin_temperature` the AOSP constant names.
        if (event.sensor.stringType != SKIN_SENSOR_TYPE) return
        val target = sink ?: return
        val at = SensorTime.epochMillisOf(event.timestamp)
        // The cached reading handed to a new listener carries the time the sensor last ran. Only
        // that one is dropped: a point that waited in the FIFO for a wake-up, even for ten minutes
        // under power-save heart rate, was taken after registering and is kept.
        if (isCachedReading(at, registeredAtMillis)) return
        // NaN ambient, not a placeholder: the recorder owns the missing-ambient policy.
        target.onReadings(listOf(SkinReading(at, event.values[0], Float.NaN, isMeasuredSkinPoint(event.accuracy))))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * The non-wake-up variant where there is one: recording all day, it must not wake the
     * processor every ~30 s; its points wait in the FIFO for the next wake-up (the heart
     * rate's batches come about every 25 s on Pixel).
     */
    private fun skinSensor(): Sensor? = sensorManager.getSensorList(Sensor.TYPE_ALL)
        .filter { it.stringType == SKIN_SENSOR_TYPE }
        .let { found -> found.firstOrNull { !it.isWakeUpSensor } ?: found.firstOrNull() }

    companion object {
        private const val SKIN_SENSOR_TYPE = "com.google.sensor.skin_temperature"

        /** The sensor is on-change anyway; the delay asks for no more than its own ~30 s cadence. */
        private const val SAMPLING = SensorManager.SENSOR_DELAY_NORMAL

        /**
         * A point the sensor itself stands behind: the Pixel Watch reports
         * medium accuracy or higher while worn. The narrow pure seam the unit
         * tests hold — the accuracy threshold is the sensor contract's only
         * branch, everything else needs a real watch.
         */
        /** A reading older than this before registering is the sensor's cached one; younger is as good as current. */
        private const val CACHED_MARGIN_MILLIS = 30_000L

        /** Whether a point taken at [eventAtMillis] is the cached reading a listener registered at [registeredAtMillis] is first handed. */
        internal fun isCachedReading(eventAtMillis: Long, registeredAtMillis: Long): Boolean =
            eventAtMillis < registeredAtMillis - CACHED_MARGIN_MILLIS

        internal fun isMeasuredSkinPoint(accuracy: Int): Boolean =
            accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
    }
}
