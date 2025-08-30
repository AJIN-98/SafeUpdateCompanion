package com.example.safeupdatecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safeupdatecompanion.model.DeviceHealth
import com.example.safeupdatecompanion.model.UpdateReadiness
import com.example.safeupdatecompanion.utils.HealthChecker
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeUpdateApp()
            SetStatusBarColor()
        }
    }
}

@Composable
fun SetStatusBarColor() {
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color(0xFF6C63FF),
            darkIcons = false
        )
    }
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF6C63FF), Color(0xFF3F51B5))),
                    shape = RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeUpdateApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var deviceHealth by remember { mutableStateOf<DeviceHealth?>(null) }
    var readiness by remember { mutableStateOf<UpdateReadiness?>(null) }
    var checksStarted by remember { mutableStateOf(false) }
    var checkProgress by remember { mutableStateOf(List(8) { false }) }
    var networkSpeed by remember { mutableStateOf("Calculating...") }

    val checkNames = listOf(
        "Battery", "Battery Temp", "Storage", "Network",
        "Device Age", "RAM Usage", "CPU Load", "CPU Temp"
    )

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Safe Update Companion",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF6C63FF)
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            GradientButton("Check Status") {
                checksStarted = true
                deviceHealth = HealthChecker.getDeviceHealth(context)
                readiness = deviceHealth?.let { HealthChecker.calculateUpdateReadiness(it) }

                scope.launch { networkSpeed = measureNetworkSpeed() }

                scope.launch {
                    for (i in checkProgress.indices) {
                        delay(500)
                        checkProgress = checkProgress.mapIndexed { index, _ -> index <= i }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(visible = checksStarted && deviceHealth != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    checkNames.forEachIndexed { i, name ->
                        val dh = deviceHealth!!
                        val valueText = when (name) {
                            "Battery" -> "${dh.batteryLevel}%"
                            "Battery Temp" -> "${dh.batteryTemperature}°C"
                            "Storage" -> "${dh.storageFreePercent}% free"
                            "Network" -> networkSpeed
                            "Device Age" -> "${dh.deviceAgeScore} Years"
                            "RAM Usage" -> "${dh.ramUsagePercent}%"
                            "CPU Load" -> "${dh.cpuLoadPercent}%"
                            "CPU Temp" -> "${dh.cpuTemperature}°C"
                            else -> ""
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (checkProgress[i]) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .animateContentSize(tween(500)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (checkProgress[i]) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                } else {
                                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(name, style = MaterialTheme.typography.titleMedium)
                                    Text(valueText, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (checkProgress.all { it } && readiness != null) {
                val scoreColor = when (readiness!!.status) {
                    "Safe" -> Color(0xFF4CAF50)
                    "Warning" -> Color(0xFFFFA000)
                    else -> Color(0xFFD32F2F)
                }

                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = readiness!!.score / 100f,
                                strokeWidth = 10.dp,
                                color = scoreColor,
                                modifier = Modifier.size(120.dp)
                            )
                            Text("${readiness!!.score}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = scoreColor))
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Total Score: ${readiness!!.score} / 100", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        Text("Status: ${readiness!!.status}", style = MaterialTheme.typography.titleLarge, color = scoreColor)

                        if (readiness!!.suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Suggestions:", style = MaterialTheme.typography.titleMedium)
                            readiness!!.suggestions.forEach { suggestion ->
                                Text("• $suggestion", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Network speed measurement
suspend fun measureNetworkSpeed(): String = withContext(Dispatchers.IO) {
    try {
        val testUrl = URL("https://nbg1-speed.hetzner.com/100MB.bin")
        val connection = testUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val startTime = System.currentTimeMillis()
        var downloadedBytes = 0L
        BufferedInputStream(connection.inputStream).use { input ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1 && downloadedBytes < 1024 * 1024) {
                downloadedBytes += bytesRead
            }
        }
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        val speedMbps = downloadedBytes * 8 / 1024.0 / 1024.0 / elapsedTime
        connection.disconnect()
        String.format("%.2f Mbps", speedMbps)
    } catch (e: Exception) {
        "Unable to measure"
    }
}
