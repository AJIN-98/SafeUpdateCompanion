package com.example.safeupdatecompanion.model

data class DeviceHealth(
    val batteryLevel: Int,
    val batteryTemperature: Float,
    val storageFreePercent: Int,
    val isNetworkStable: Boolean,
    val deviceAgeScore: Int,
    val ramUsagePercent: Int = 0,
    val cpuLoadPercent: Int = 0,
    val cpuTemperature: Float = 0f
)
data class UpdateReadiness(
    val score: Int, // 0–100
    val status: String, // "Safe", "Warning", "Risky"
    val suggestions: List<String>
)
