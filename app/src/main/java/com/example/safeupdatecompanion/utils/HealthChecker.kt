package com.example.safeupdatecompanion.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import com.example.safeupdatecompanion.model.DeviceHealth
import com.example.safeupdatecompanion.model.UpdateReadiness
import java.io.File
import java.io.RandomAccessFile

object HealthChecker {

    fun getDeviceHealth(context: Context): DeviceHealth {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryTemp = getBatteryTemperature(context)
        val storageFreePercent = getStorageFreePercent()
        val networkStable = isNetworkStable(context)
        val deviceAgeScore = calculateDeviceAge()
        val ramUsagePercent = getRAMUsagePercent(context)
        val cpuLoadPercent = getCPULoadPercent()
        val cpuTemperature = getCPUTemperature(context)

        return DeviceHealth(
            batteryLevel,
            batteryTemp,
            storageFreePercent,
            networkStable,
            deviceAgeScore,
            ramUsagePercent,
            cpuLoadPercent,
            cpuTemperature
        )
    }

    // Device age score: 100 = new, 10 = very old
    private fun calculateDeviceAge(): Int {
        val buildTime = Build.TIME
        val currentTime = System.currentTimeMillis()
        val ageInYears = ((currentTime - buildTime) / (1000 * 60 * 60 * 24 * 365)).toInt()

        return ageInYears
    }

    private fun getBatteryTemperature(context: Context): Float {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
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

    private fun getRAMUsagePercent(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // Include cached memory for more realistic "used RAM"
        val used = memoryInfo.totalMem - memoryInfo.availMem
        val usagePercent = ((used.toDouble() / memoryInfo.totalMem) * 100).toInt()
        return usagePercent.coerceIn(0, 100)
    }



    private fun getCPULoadPercent(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load1 = reader.readLine().split("\\s+".toRegex()).drop(1).map { it.toLong() }
            reader.close()

            val idle1 = load1[3] + load1[4] // idle + iowait
            val total1 = load1.sum()

            Thread.sleep(500)

            val reader2 = RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine().split("\\s+".toRegex()).drop(1).map { it.toLong() }
            reader2.close()

            val idle2 = load2[3] + load2[4]
            val total2 = load2.sum()

            val usage = (1.0 - (idle2 - idle1).toDouble() / (total2 - total1)) * 100
            usage.coerceIn(0.0, 100.0).toInt()
        } catch (e: Exception) {
            // fallback using process stats
            try {
                val usage = (Debug.getNativeHeapAllocatedSize().toDouble() /
                        Debug.getNativeHeapSize().toDouble()) * 100
                usage.coerceIn(0.0, 100.0).toInt()
            } catch (_: Exception) {
                0
            }
        }
    }


    private fun getCPUTemperature(context: Context): Float {
        return try {
            val thermalFile = File("/sys/class/thermal/thermal_zone0/temp")
            if (thermalFile.exists()) {
                val temp = thermalFile.readText().trim().toFloat()
                return if (temp > 1000) temp / 1000f else temp
            }

            // Use battery temp as fallback
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val batteryTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            batteryTemp / 10f
        } catch (e: Exception) {
            0f
        }
    }



    fun calculateUpdateReadiness(health: DeviceHealth): UpdateReadiness {
        var score = 100
        val suggestions = mutableListOf<String>()

        if (health.batteryLevel < 50) { score -= 30; suggestions.add("Charge battery to at least 50%") }
        if (health.batteryTemperature > 40f) { score -= 20; suggestions.add("Cool down device before updating") }
        if (health.storageFreePercent < 20) { score -= 20; suggestions.add("Free up storage space") }
        if (!health.isNetworkStable) { score -= 20; suggestions.add("Connect to a stable network") }

        // Device age penalties based on actual years
        when (health.deviceAgeScore) {
            0, 1 -> {
                // New device, no penalty
            }
            2 -> {
                score -= 15
                suggestions.add("Device is 2 years old; minor caution advised")
            }
            3 -> {
                score -= 30
                suggestions.add("Device is 3 years old; consider updating apps first")
            }
            4 -> {
                score -= 45
                suggestions.add("Device is 4 years old; update only essential apps")
            }
            else -> {
                score -= 60
                suggestions.add("Device is 5+ years old; updating may cause issues")
            }
        }


        // RAM/CPU/CPU Temp penalties
        if (health.ramUsagePercent > 80) { score -= 30; suggestions.add("High RAM usage may slow down update") }
        if (health.cpuLoadPercent > 80) {
            score -= 30
            suggestions.add("CPU heavily loaded; consider closing apps")
        } else if (health.cpuLoadPercent > 50) {
            score -= 15
            suggestions.add("CPU under load; wait before updating")
        }

        if (health.cpuTemperature > 80f) {
            score -= 30
            suggestions.add("CPU is very hot; cool down before updating")
        } else if (health.cpuTemperature > 70f) {
            score -= 15
            suggestions.add("CPU is warm; consider waiting")
        }

        val status = when {
            score >= 80 -> "Safe"
            score >= 50 -> "Warning"
            else -> "Risky"
        }

        return UpdateReadiness(score, status, suggestions)
    }
}
