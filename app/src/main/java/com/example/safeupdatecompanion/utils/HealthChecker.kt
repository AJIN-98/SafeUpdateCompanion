package com.example.safeupdatecompanion.utils

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.os.Environment
import com.example.safeupdatecompanion.model.DeviceHealth
import com.example.safeupdatecompanion.model.UpdateReadiness

object HealthChecker {

    fun getDeviceHealth(context: Context): DeviceHealth {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryTemp = getBatteryTemperature(context)
        val storageFreePercent = getStorageFreePercent()
        val networkStable = isNetworkStable(context)
        val deviceAgeScore = 80 // Placeholder: in real app, calculate from age/performance

        return DeviceHealth(
            batteryLevel,
            batteryTemp,
            storageFreePercent,
            networkStable,
            deviceAgeScore
        )
    }

    private fun getBatteryTemperature(context: Context): Float {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10f
    }

    private fun getStorageFreePercent(): Int {
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes.toFloat()
        val total = stat.totalBytes.toFloat()
        return ((free / total) * 100).toInt()
    }

    private fun isNetworkStable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nc) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun calculateUpdateReadiness(health: DeviceHealth): UpdateReadiness {
        var score = 100
        val suggestions = mutableListOf<String>()

        if (health.batteryLevel < 50) {
            score -= 30
            suggestions.add("Charge battery to at least 50%")
        }

        if (health.batteryTemperature > 40f) {
            score -= 20
            suggestions.add("Cool down device before updating")
        }

        if (health.storageFreePercent < 20) {
            score -= 20
            suggestions.add("Free up storage space")
        }

        if (!health.isNetworkStable) {
            score -= 15
            suggestions.add("Connect to a stable network")
        }

        if (health.deviceAgeScore < 50) {
            score -= 15
            suggestions.add("Device is aged; consider updating apps first")
        }

        val status = when {
            score >= 80 -> "Safe"
            score >= 50 -> "Warning"
            else -> "Risky"
        }

        return UpdateReadiness(score, status, suggestions)
    }
}
