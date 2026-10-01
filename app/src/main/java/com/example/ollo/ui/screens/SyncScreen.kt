package com.example.ollo.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ollo.ble.BleManager
import com.example.ollo.ble.SyncEngine
import com.example.ollo.ui.viewmodel.OlloViewModel
import com.example.ui.theme.OlloAmber
import com.example.ui.theme.OlloCyan
import com.example.ui.theme.OlloDarkBg
import com.example.ui.theme.OlloDarkSurface
import com.example.ui.theme.OlloDarkSurfaceCard
import com.example.ui.theme.OlloGreen
import com.example.ui.theme.OlloRed
import com.example.ui.theme.OlloTextMuted
import com.example.ui.theme.OlloTextPrimary
import com.example.ui.theme.OlloTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    viewModel: OlloViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val storageEstimate by viewModel.storageEstimate.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Permission launcher for BLE
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.startScan()
        } else {
            viewModel.showMessage("Bluetooth permissions are required to scan for Ollo glasses")
        }
    }

    fun requestBlePermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("BLE Sync to Ollo Glasses", fontWeight = FontWeight.Bold, color = OlloCyan)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_from_sync")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OlloTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OlloDarkBg)
            )
        },
        containerColor = OlloDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // BLE Connection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (icon, color, label) = when (val state = connectionState) {
                                is BleManager.ConnectionState.Connected -> Triple(Icons.Default.BluetoothConnected, OlloGreen, "Connected: ${state.deviceName}")
                                is BleManager.ConnectionState.Connecting -> Triple(Icons.Default.BluetoothSearching, OlloAmber, "Connecting...")
                                is BleManager.ConnectionState.Scanning -> Triple(Icons.Default.BluetoothSearching, OlloCyan, "Scanning...")
                                is BleManager.ConnectionState.Error -> Triple(Icons.Default.Error, OlloRed, "Error")
                                is BleManager.ConnectionState.Disconnected -> Triple(Icons.Default.Bluetooth, OlloTextMuted, "Disconnected")
                            }
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }

                        if (connectionState is BleManager.ConnectionState.Connected) {
                            OutlinedButton(
                                onClick = { viewModel.disconnectDevice() },
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OlloRed)
                            ) {
                                Text("Disconnect", fontSize = 12.sp)
                            }
                        }
                    }

                    if (connectionState is BleManager.ConnectionState.Connected) {
                        val state = connectionState as BleManager.ConnectionState.Connected
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OlloDarkSurfaceCard, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Address: ${state.address}", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                            Text("MTU: ${state.mtu} (Max Write: ${viewModel.bleManager.maxPayloadSize}B)", style = MaterialTheme.typography.bodySmall, color = OlloCyan)
                        }
                    }

                    if (connectionState is BleManager.ConnectionState.Error) {
                        val state = connectionState as BleManager.ConnectionState.Error
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = OlloRed, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scan / Stop button
                    if (connectionState !is BleManager.ConnectionState.Connected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (connectionState is BleManager.ConnectionState.Scanning) {
                                        viewModel.stopScan()
                                    } else {
                                        requestBlePermissionsAndScan()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("scan_ble_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OlloCyan,
                                    contentColor = Color(0xFF00363D)
                                )
                            ) {
                                if (connectionState is BleManager.ConnectionState.Scanning) {
                                    CircularProgressIndicator(color = Color(0xFF00363D), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop Scan", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan for Ollo", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Discovered Devices list
                    if (discoveredDevices.isNotEmpty() && connectionState !is BleManager.ConnectionState.Connected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Discovered Devices:", style = MaterialTheme.typography.labelMedium, color = OlloTextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        discoveredDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OlloDarkSurfaceCard)
                                    .clickable {
                                        viewModel.connectDevice(
                                            address = device.address,
                                            name = device.name,
                                            simulated = device.name.contains("Virtual")
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name, fontWeight = FontWeight.SemiBold, color = OlloTextPrimary)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = OlloTextMuted)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = OlloCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connect", style = MaterialTheme.typography.labelMedium, color = OlloCyan)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // Virtual Simulator Mode Callout
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF13222F))
                            .border(1.dp, OlloCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Virtual Ollo Glasses (ESP32 Simulator)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = OlloCyan
                            )
                            Text(
                                text = "Simulates firmware LittleFS streaming & deduplication for testing without hardware.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = OlloTextSecondary
                            )
                        }
                        Switch(
                            checked = settings.virtualPeripheralEnabled,
                            onCheckedChange = { viewModel.toggleVirtualMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OlloCyan,
                                checkedTrackColor = OlloCyan.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("virtual_esp_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Summary & Execution Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Deck Sync Payload",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OlloCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Cards", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                            Text("${storageEstimate.cardCount}", fontWeight = FontWeight.Bold, color = OlloTextPrimary)
                        }
                        Column {
                            Text("Unique Images", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                            Text("${storageEstimate.uniqueImageCount}", fontWeight = FontWeight.Bold, color = OlloTextPrimary)
                        }
                        Column {
                            Text("Wire Size", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                            Text("${storageEstimate.totalEstimatedBytes / 1024} KB", fontWeight = FontWeight.Bold, color = OlloCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sync Status Section
                    when (val status = syncStatus) {
                        is SyncEngine.SyncStatus.Idle -> {
                            Button(
                                onClick = { viewModel.startSync() },
                                enabled = connectionState is BleManager.ConnectionState.Connected && storageEstimate.cardCount > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("start_sync_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OlloCyan,
                                    contentColor = Color(0xFF00363D),
                                    disabledContainerColor = Color(0xFF242F3D),
                                    disabledContentColor = OlloTextMuted
                                )
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Deck to Glasses", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            if (connectionState !is BleManager.ConnectionState.Connected) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Connect to Ollo or enable Virtual Simulator above to sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OlloAmber
                                )
                            }
                        }

                        is SyncEngine.SyncStatus.Syncing -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = status.stepDescription,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OlloCyan
                                    )
                                    Text(
                                        text = "${(status.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = OlloCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { status.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = OlloCyan,
                                    trackColor = Color(0xFF263238)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Transferred: ${status.bytesTransferred} B",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OlloTextSecondary
                                    )
                                    if (status.skippedImages > 0) {
                                        Text(
                                            "Skipped (cached): ${status.skippedImages}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OlloGreen
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.cancelSync() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OlloRed)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel Sync")
                                }
                            }
                        }

                        is SyncEngine.SyncStatus.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OlloGreen.copy(alpha = 0.12f))
                                    .border(1.dp, OlloGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OlloGreen, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Completed Successfully!", fontWeight = FontWeight.Bold, color = OlloGreen)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• ${status.cardsSynced} cards stored in flash", style = MaterialTheme.typography.bodySmall, color = OlloTextPrimary)
                                Text("• ${status.imagesSynced} images transferred (${status.imagesSkipped} skipped / deduplicated)", style = MaterialTheme.typography.bodySmall, color = OlloTextPrimary)
                                Text("• Total wire bytes: ${status.totalBytes} B in ${status.elapsedMs} ms", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.resetSyncStatus() },
                                    colors = ButtonDefaults.buttonColors(containerColor = OlloGreen, contentColor = Color(0xFF003923)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is SyncEngine.SyncStatus.Failed -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OlloRed.copy(alpha = 0.12f))
                                    .border(1.dp, OlloRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = OlloRed, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Failed", fontWeight = FontWeight.Bold, color = OlloRed)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(status.error, style = MaterialTheme.typography.bodyMedium, color = OlloTextPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetSyncStatus() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Dismiss")
                                    }
                                    if (status.canRetry) {
                                        Button(
                                            onClick = { viewModel.startSync() },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = OlloCyan, contentColor = Color(0xFF00363D))
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
