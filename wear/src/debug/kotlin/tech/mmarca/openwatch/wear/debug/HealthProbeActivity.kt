package tech.mmarca.openwatch.wear.debug

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPoint
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.data.UserActivityInfo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fork-only survey probe for the Pixel Watch (debug builds only). It answers, on the watch,
 * the survey questions a dumpsys cannot:
 *
 * 1. what Health Services offers *this app* (passive, measure, exercise capabilities);
 * 2. whether Health Services' live heart rate reaches this app, and how often;
 * 3. whether this app may subscribe to the unnamed `health_services.device_private.<n>` passive
 *    types the vendor's health app uses, and what bytes they carry;
 * 4. what the vendor sensors behind public grants carry (`heart_rate_extended`,
 *    `skin_temperature`, `step_cadence`): their value arrays, raw;
 * 5. whether every type the device *lists* (passive and measure: on Wear OS 7 that can include
 *    HRV, respiratory rate, skin temperature, SpO2, VO2 max and blood pressure) is delivered.
 *
 * Every line goes to `files/debug/probe-<time>.jsonl`, pulled with `run-as`. It writes nothing
 * else and stops itself. Start it with an optional `--ei seconds <n>` (default 90).
 */
class HealthProbeActivity : ComponentActivity() {

    private lateinit var out: File
    private lateinit var screen: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        screen = TextView(this).apply { textSize = 12f; setPadding(24, 48, 24, 48) }
        setContentView(ScrollView(this).apply { addView(screen) })
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        out = File(filesDir, "debug/probe-$stamp.jsonl").apply { parentFile?.mkdirs() }
        val seconds = intent.getIntExtra("seconds", 90).coerceIn(15, 900)
        lifecycleScope.launch { run(seconds) }
    }

    private fun emit(kind: String, fill: JSONObject.() -> Unit = {}) {
        val line = JSONObject().put("t", System.currentTimeMillis()).put("kind", kind).apply(fill)
        out.appendText(line.toString() + "\n")
        runOnUiThread { screen.append("$kind\n") }
    }

    private suspend fun run(seconds: Int) {
        emit("start") {
            put("model", Build.MODEL); put("build", Build.FINGERPRINT); put("sdk", Build.VERSION.SDK_INT)
            put("seconds", seconds)
        }
        val client = HealthServices.getClient(this)
        val listed = capabilities(client)
        val sensors = rawSensors()
        // Off by default: health-services-client 1.1.0 crashes the app decoding a Blood Pressure
        // point (ClassCastException DataProto$Value$DoubleArray → double[], Wear OS 7 emulator,
        // 2026-11-27), before any callback of ours runs. `--ez measureAll true` to retry it.
        val measureTypes = if (intent.getBooleanExtra("measureAll", false)) listed.measure + DataType.HEART_RATE_BPM else setOf(DataType.HEART_RATE_BPM)
        (listed.measure - measureTypes).forEach { emit("measure.other.skipped") { put("type", it.name) } }
        val measures = measureTypes.mapNotNull { measure(client, it) }
        val passive = passive(client, seconds, listed.passive)
        delay(seconds * 1000L)
        runCatching { client.passiveMonitoringClient.flushAsync().await() }
            .onFailure { e -> emit("passive.flush.error") { put("error", e.toString()) } }
        delay(5_000)
        passive?.let { runCatching { client.passiveMonitoringClient.clearPassiveListenerCallbackAsync().await() } }
        measures.forEach { (type, callback) -> runCatching { client.measureClient.unregisterMeasureCallbackAsync(type, callback).await() } }
        sensors()
        emit("done") { put("file", out.name) }
        finish()
    }

    /** What the device lists for this app, to subscribe to all of it. */
    private class Listed(val passive: Set<DataType<*, *>>, val measure: Set<DeltaDataType<*, *>>)

    private suspend fun capabilities(client: androidx.health.services.client.HealthServicesClient): Listed {
        var passive: Set<DataType<*, *>> = emptySet()
        var measure: Set<DeltaDataType<*, *>> = emptySet()
        runCatching { client.passiveMonitoringClient.getCapabilitiesAsync().await() }
            .onSuccess { c ->
                passive = c.supportedDataTypesPassiveMonitoring
                emit("caps.passive") {
                    put("dataTypes", names(c.supportedDataTypesPassiveMonitoring))
                    put("goalTypes", names(c.supportedDataTypesPassiveGoals))
                    put("healthEvents", JSONArray(c.supportedHealthEventTypes.map { it.toString() }))
                    put("userActivityStates", JSONArray(c.supportedUserActivityStates.map { it.toString() }))
                }
            }
            .onFailure { e -> emit("caps.passive.error") { put("error", e.toString()) } }
        runCatching { client.measureClient.getCapabilitiesAsync().await() }
            .onSuccess { c ->
                measure = c.supportedDataTypesMeasure
                emit("caps.measure") { put("dataTypes", names(c.supportedDataTypesMeasure)) }
            }
            .onFailure { e -> emit("caps.measure.error") { put("error", e.toString()) } }
        runCatching { client.exerciseClient.getCapabilitiesAsync().await() }
            .onSuccess { c ->
                emit("caps.exercise") {
                    put("exerciseTypes", JSONArray(c.supportedExerciseTypes.map { it.name }))
                    val all = c.supportedExerciseTypes.flatMap { c.getExerciseTypeCapabilities(it).supportedDataTypes }
                    put("dataTypesUnion", names(all.toSet()))
                }
            }
            .onFailure { e -> emit("caps.exercise.error") { put("error", e.toString()) } }
        return Listed(passive, measure)
    }

    /** One measure callback for [type]; heart rate keeps its old `measure.hr.*` labels. */
    private fun measure(
        client: androidx.health.services.client.HealthServicesClient,
        type: DeltaDataType<*, *>,
    ): Pair<DeltaDataType<*, *>, MeasureCallback>? {
        val label = if (type == DataType.HEART_RATE_BPM) "measure.hr" else "measure.other"
        val callback = object : MeasureCallback {
            override fun onRegistered() = emit("$label.registered") { put("type", type.name) }
            override fun onRegistrationFailed(throwable: Throwable) =
                emit("$label.failed") { put("type", type.name); put("error", throwable.toString()) }
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) =
                emit("$label.availability") { put("type", dataType.name); put("value", availability.toString()) }
            override fun onDataReceived(data: DataPointContainer) =
                emit("$label.data") { put("points", points(data)) }
        }
        return runCatching { client.measureClient.registerMeasureCallback(type, callback); type to callback }
            .onFailure { e -> emit("$label.error") { put("type", type.name); put("error", e.toString()) } }
            .getOrNull()
    }

    /**
     * One passive callback per app: try the vendor's private types first (the question that
     * matters), and fall back to the public set only if the private registration is refused.
     * The public set is always included so data from it shows the callback works at all.
     */
    private fun passive(
        client: androidx.health.services.client.HealthServicesClient,
        seconds: Int,
        listed: Set<DataType<*, *>>,
    ): PassiveListenerCallback? {
        val known = setOf(
            DataType.HEART_RATE_BPM, DataType.STEPS, DataType.DISTANCE, DataType.CALORIES, DataType.FLOORS,
            DataType.ELEVATION_GAIN, DataType.STEPS_DAILY, DataType.CALORIES_DAILY, DataType.DISTANCE_DAILY,
            DataType.FLOORS_DAILY,
        )
        // Everything the device lists, by its own DataType objects: names this library has no
        // constant for (Wear OS 7's vitals) still round-trip.
        val public = known + listed
        val private: Set<DataType<*, *>> = PRIVATE_IDS.map {
            DeltaDataType("health_services.device_private.$it", DataType.TimeType.INTERVAL, ByteArray::class.java)
        }.toSet()
        fun register(label: String, types: Set<DataType<*, *>>, onFail: () -> Unit): PassiveListenerCallback {
            val callback = object : PassiveListenerCallback {
                override fun onRegistered() = emit("passive.$label.registered") { put("types", names(types)) }
                override fun onRegistrationFailed(throwable: Throwable) {
                    emit("passive.$label.failed") { put("error", throwable.toString()) }
                    onFail()
                }
                override fun onNewDataPointsReceived(dataPoints: DataPointContainer) =
                    emit("passive.$label.data") { put("points", points(dataPoints)) }
                override fun onUserActivityInfoReceived(info: UserActivityInfo) =
                    emit("passive.$label.activity") { put("state", info.userActivityState.toString()) }
                override fun onPermissionLost() = emit("passive.$label.permissionLost")
            }
            val config = PassiveListenerConfig.builder().setDataTypes(types).setShouldUserActivityInfoBeRequested(true).build()
            client.passiveMonitoringClient.setPassiveListenerCallback(config, callback)
            return callback
        }
        return runCatching {
            register("private", public + private) {
                lifecycleScope.launch { runCatching { register("public", public) {} } }
            }
        }.onFailure { e ->
            emit("passive.private.error") { put("error", e.toString()) }
            runCatching { register("public", public) {} }
        }.getOrNull().also { emit("passive.window") { put("seconds", seconds) } }
    }

    /** Listens to the vendor sensors behind public grants; returns the stop action. */
    private fun rawSensors(): () -> Unit {
        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val wanted = setOf(
            "com.google.sensor.heart_rate_extended", "com.google.sensor.skin_temperature",
            "com.google.sensor.step_cadence", Sensor.STRING_TYPE_HEART_RATE,
        )
        val listeners = mutableListOf<SensorEventListener>()
        manager.getSensorList(Sensor.TYPE_ALL).filter { it.stringType in wanted && !it.isWakeUpSensor }.forEach { sensor ->
            var count = 0
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    count++
                    if (count <= MAX_EVENTS_LOGGED) {
                        emit("sensor.event") {
                            put("type", sensor.stringType); put("n", count); put("accuracy", event.accuracy)
                            put("values", JSONArray(event.values.map { it.toDouble() }))
                        }
                    }
                }
                override fun onAccuracyChanged(s: Sensor, accuracy: Int) =
                    emit("sensor.accuracy") { put("type", sensor.stringType); put("accuracy", accuracy) }
            }
            val ok = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            emit("sensor.register") {
                put("type", sensor.stringType); put("name", sensor.name); put("ok", ok)
                put("maxRange", sensor.maximumRange.toDouble()); put("resolution", sensor.resolution.toDouble())
                put("reportingMode", sensor.reportingMode)
            }
            if (ok) listeners += listener
        }
        return { listeners.forEach(manager::unregisterListener) }
    }

    private fun names(types: Set<DataType<*, *>>) = JSONArray(types.map { it.name }.sorted())

    private fun points(data: DataPointContainer): JSONArray = JSONArray().apply {
        val all: List<DataPoint<*>> = data.sampleDataPoints + data.intervalDataPoints
        all.groupBy { it.dataType }.forEach { (type, list) ->
            list.take(MAX_POINTS_LOGGED).forEach { point -> put(point(type, point)) }
            if (list.size > MAX_POINTS_LOGGED) put(JSONObject().put("type", type.name).put("more", list.size - MAX_POINTS_LOGGED))
        }
    }

    private fun point(type: DataType<*, *>, point: DataPoint<*>): JSONObject = JSONObject().apply {
        put("type", type.name)
        val value: Any? = when (point) {
            is SampleDataPoint<*> -> point.value.also {
                put("at", point.timeDurationFromBoot.toMillis()); putAccuracy(point.accuracy); putMeta(point.metadata)
            }
            is IntervalDataPoint<*> -> point.value.also {
                put("from", point.startDurationFromBoot.toMillis()); put("to", point.endDurationFromBoot.toMillis())
                putAccuracy(point.accuracy); putMeta(point.metadata)
            }
            else -> point.toString()
        }
        when (value) {
            is ByteArray -> { put("bytes", value.size); put("hex", value.joinToString("") { "%02x".format(it) }.take(MAX_HEX)) }
            is DoubleArray -> put("values", JSONArray(value.toList()))
            is Number, is Boolean, is String -> put("value", value)
            else -> put("value", value.toString())
        }
    }

    private fun JSONObject.putAccuracy(accuracy: Any?) { if (accuracy != null) put("accuracy", accuracy.toString()) }

    private fun JSONObject.putMeta(meta: Bundle) {
        if (meta.isEmpty) return
        put("meta", JSONObject().apply { meta.keySet().forEach { key -> put(key, @Suppress("DEPRECATION") meta.get(key).toString()) } })
    }

    private companion object {
        /** The passive types the vendor app subscribes to (dumpsys, 2026-11-26), plus the others the hub lists. */
        val PRIVATE_IDS = listOf(65537, 65538, 65539, 65540, 65541, 65542, 65543, 65545, 65547, 65548, 65549)
        const val MAX_EVENTS_LOGGED = 40
        const val MAX_POINTS_LOGGED = 20
        const val MAX_HEX = 512
    }
}
