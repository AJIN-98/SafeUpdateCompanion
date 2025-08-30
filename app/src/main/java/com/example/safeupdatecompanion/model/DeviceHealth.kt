package com.example.safeupdatecompanion.model

data class DeviceHealth(
    val batteryLevel: Int,
    val batteryTemperature: Float,
    val storageFreePercent: Int,
    val isNetworkStable: Boolean,
    val deviceAgeScore: Int, // 0–100 based on age/performance
)

data class UpdateReadiness(
    val score: Int, // 0–100
    val status: String, // "Safe", "Warning", "Risky"
    val suggestions: List<String>
)
