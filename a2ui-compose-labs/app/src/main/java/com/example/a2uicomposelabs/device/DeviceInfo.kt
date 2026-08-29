package com.example.a2uicomposelabs.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import kotlin.math.roundToInt

/**
 * What this phone can tell us about itself.
 *
 * This is the sharpest version of the rule the other demos keep repeating, because here the
 * model is not merely *discouraged* from inventing the data — it **cannot possibly know it.**
 * No model knows how many sensors are in the device in your hand, what its battery temperature
 * is right now, or whether it is thermally throttled. It can only say *what to show*.
 *
 * It is also why the same prompt produces a different screen on different hardware, with no
 * code change: an emulator lists a handful of virtual sensors, a flagship lists thirty. The
 * agent wrote one template; the device decided how long the list is.
 *
 * Everything here is readable with no runtime permission.
 */
object DeviceInfo {

    /** A caption and a value, ready for a StatTile. */
    data class Stat(val label: String, val value: String, val detail: String = "")

    /** Battery, as several tiles: level, temperature, and how it is charging. */
    fun battery(context: Context): List<Stat> {
        val status: Intent? =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else -1
        val tenthsC = status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val state = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        return listOf(
            Stat("Battery", if (percent >= 0) "$percent%" else "—", chargingLabel(state, plugged)),
            Stat("Temperature", if (tenthsC > 0) "%.1f°C".format(tenthsC / 10f) else "—"),
            Stat("Health", healthLabel(status?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1)),
        )
    }

    /** Memory, storage, thermal state and what the device actually is. */
    fun system(context: Context): List<Stat> {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        return buildList {
            add(Stat("Device", "${Build.MANUFACTURER} ${Build.MODEL}", "Android ${Build.VERSION.RELEASE}"))
            add(
                Stat(
                    "Memory free",
                    gb(memory.availMem),
                    "of ${gb(memory.totalMem)}" + if (memory.lowMemory) " · low" else "",
                )
            )
            add(Stat("Storage free", gb(freeBytes), "of ${gb(totalBytes)}"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val power = context.getSystemService(PowerManager::class.java)
                add(Stat("Thermal", thermalLabel(power?.currentThermalStatus ?: -1)))
            }
        }
    }

    /** One row per sensor. The length of this list is the part no model could have guessed. */
    fun sensors(context: Context): List<Stat> {
        val manager = context.getSystemService(SensorManager::class.java) ?: return emptyList()
        return manager.getSensorList(Sensor.TYPE_ALL).map { sensor ->
            Stat(
                label = sensor.vendor.take(28),
                value = sensor.name.take(38),
                detail = "%.2f mA".format(sensor.power),
            )
        }
    }

    /**
     * A live sample: the numbers that move while you watch.
     *
     * CPU is *this app's* share, from [Process.getElapsedCpuTime] against wall clock, because
     * system-wide CPU is not readable without privileges on a modern Android. GPU utilisation is
     * not available to an ordinary app at all — vendor APIs only — so it is honestly absent
     * rather than faked.
     */
    class Sampler {
        private var lastCpuMs = 0L
        private var lastWallMs = 0L
        private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        /** Percentage of one device's worth of CPU this app used since the previous call. */
        fun cpuPercent(): Float {
            val cpuMs = Process.getElapsedCpuTime()
            val wallMs = SystemClock.elapsedRealtime()
            val used = if (lastWallMs == 0L) 0f else {
                val wallDelta = (wallMs - lastWallMs).coerceAtLeast(1L)
                (cpuMs - lastCpuMs).toFloat() / wallDelta / cores * 100f
            }
            lastCpuMs = cpuMs
            lastWallMs = wallMs
            return used.coerceIn(0f, 100f)
        }

        /** Battery, memory, CPU and link, sampled now. Safe to call once a second. */
        fun live(context: Context): List<Stat> {
            val battery: Intent? =
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else -1
            val tenthsC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memory = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
            val usedRatio =
                if (memory.totalMem > 0) 1f - memory.availMem.toFloat() / memory.totalMem else 0f

            return listOf(
                Stat("Battery", if (percent >= 0) "$percent%" else "—", batteryTemp(tenthsC)),
                Stat("App CPU", "%.1f%%".format(cpuPercent()), "$cores cores"),
                Stat("Memory used", "${(usedRatio * 100).roundToInt()}%", "${gb(memory.availMem)} free"),
                Stat("Network", link(context)),
            )
        }

        private fun batteryTemp(tenthsC: Int) =
            if (tenthsC > 0) "%.1f°C".format(tenthsC / 10f) else ""
    }

    /** What we are connected through. Needs only the install-time ACCESS_NETWORK_STATE. */
    private fun link(context: Context): String {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "—"
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "Offline"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    private fun gb(bytes: Long): String = "%.1f GB".format(bytes / 1024f / 1024f / 1024f)

    private fun chargingLabel(status: Int, plugged: Int): String = when {
        status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
        status == BatteryManager.BATTERY_STATUS_CHARGING ->
            "Charging" + when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> " (AC)"
                BatteryManager.BATTERY_PLUGGED_USB -> " (USB)"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> " (wireless)"
                else -> ""
            }
        status == BatteryManager.BATTERY_STATUS_DISCHARGING -> "On battery"
        else -> "—"
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "—"
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "—"
    }
}
