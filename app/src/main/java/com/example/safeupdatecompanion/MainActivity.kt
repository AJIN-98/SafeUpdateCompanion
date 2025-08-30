package com.example.safeupdatecompanion

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.safeupdatecompanion.model.DeviceHealth
import com.example.safeupdatecompanion.model.UpdateReadiness
import com.example.safeupdatecompanion.utils.HealthChecker
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
    var checkProgress by remember { mutableStateOf(listOf(false, false, false, false, false)) }
    var networkSpeed by remember { mutableStateOf("Calculating...") }

    val checkNames = listOf("Battery", "Storage", "Network", "Device Age", "Battery Temp")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Safe Update Companion",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        checksStarted = true
                        deviceHealth = HealthChecker.getDeviceHealth(context)
                        readiness = deviceHealth?.let { HealthChecker.calculateUpdateReadiness(it) }

                        // Measure network speed asynchronously
                        scope.launch {
                            networkSpeed = measureNetworkSpeed()
                        }

                        // Animate checks sequentially using coroutine
                        scope.launch {
                            for (i in checkProgress.indices) {
                                delay(500)
                                checkProgress = checkProgress.mapIndexed { index, _ -> index <= i }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Check Status", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(30.dp))

                if (checksStarted && deviceHealth != null) {
                    val dh = deviceHealth!!
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(checkNames.size) { i ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .animateContentSize(animationSpec = tween(500))
                            ) {
                                Checkbox(
                                    checked = checkProgress.getOrElse(i) { false },
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                val valueText = when (checkNames[i]) {
                                    "Battery" -> "${dh.batteryLevel}%"
                                    "Battery Temp" -> "${dh.batteryTemperature}°C"
                                    "Storage" -> "${dh.storageFreePercent}% free"
                                    "Network" -> networkSpeed
                                    "Device Age" -> "${dh.deviceAgeScore}/100"
                                    else -> ""
                                }
                                Text("${checkNames[i]}: $valueText", fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Show final score once all checks complete
                if (checkProgress.all { it } && readiness != null) {
                    val scoreColor = when (readiness!!.status) {
                        "Safe" -> MaterialTheme.colorScheme.primary
                        "Warning" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }

                    Text(
                        "Update Readiness Score: ${readiness!!.score}",
                        style = MaterialTheme.typography.titleLarge,
                        color = scoreColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "Status: ${readiness!!.status}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (readiness!!.suggestions.isNotEmpty()) {
                        Text("Suggestions:", style = MaterialTheme.typography.titleMedium)
                        readiness!!.suggestions.forEach { suggestion ->
                            Text("- $suggestion")
                        }
                    }
                }
            }
        }
    )
}

suspend fun measureNetworkSpeed(): String = withContext(Dispatchers.IO) {
    try {
        val testUrl = URL("https://speed.hetzner.de/100MB.bin")
        val connection = testUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.connect()

        val startTime = System.currentTimeMillis()
        var downloadedBytes = 0L
        BufferedInputStream(connection.inputStream).use { input ->
            val buffer = ByteArray(8 * 1024) // 8KB buffer
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1 && downloadedBytes < 1024 * 500) {
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
