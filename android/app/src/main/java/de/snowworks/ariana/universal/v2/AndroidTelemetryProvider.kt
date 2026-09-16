package de.snowworks.ariana.universal.v2

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager

class AndroidTelemetryProvider(private val context: Context) {
    fun read(): ArianaTelemetry {
        val app = context.applicationContext
        val battery = app.getSystemService(BatteryManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        val activity = app.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val processMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)

        return ArianaTelemetry(
            batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100),
            charging = battery.isCharging,
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else PowerManager.THERMAL_STATUS_NONE,
            availableRamMb = memory.availMem / 1024 / 1024,
            totalRamMb = memory.totalMem / 1024 / 1024,
            screenOn = power.isInteractive,
            powerSaveMode = power.isPowerSaveMode,
            processPssMb = processMemory.totalPss.toLong() / 1024,
        )
    }
}
