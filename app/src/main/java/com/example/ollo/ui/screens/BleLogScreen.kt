package com.example.ollo.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ollo.ble.BleLogEntry
import com.example.ollo.ui.viewmodel.OlloViewModel
import com.example.ui.theme.OlloCyan
import com.example.ui.theme.OlloDarkBg
import com.example.ui.theme.OlloDarkSurface
import com.example.ui.theme.OlloDarkSurfaceCard
import com.example.ui.theme.OlloGreen
import com.example.ui.theme.OlloTextMuted
import com.example.ui.theme.OlloTextPrimary
import com.example.ui.theme.OlloTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleLogScreen(
    viewModel: OlloViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val logs by viewModel.bleLogs.collectAsStateWithLifecycle()
    var filterDirection by remember { mutableStateOf<BleLogEntry.Direction?>(null) } // null = All

    val filteredLogs = remember(logs, filterDirection) {
        if (filterDirection == null) logs
        else logs.filter { it.direction == filterDirection }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BLE Packet Log", fontWeight = FontWeight.Bold, color = OlloCyan)
                        Text(
                            "Last 100 packets (raw wire hex)",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloTextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_from_log")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OlloTextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = logs.joinToString("\n") {
                                "[${it.formattedTime()}] ${it.direction} | ${it.typeName} (${it.byteLength}B) -> ${it.hexData} ${if (it.note.isNotBlank()) "(${it.note})" else ""}"
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Ollo BLE Logs", text))
                            viewModel.showMessage("Logs copied to clipboard")
                        },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs", tint = OlloCyan)
                    }
                    IconButton(
                        onClick = { viewModel.clearBleLogs() },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = OlloTextSecondary)
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
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterDirection == null,
                    onClick = { filterDirection = null },
                    label = { Text("All (${logs.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OlloCyan.copy(alpha = 0.2f),
                        selectedLabelColor = OlloCyan
                    )
                )
                FilterChip(
                    selected = filterDirection == BleLogEntry.Direction.TX,
                    onClick = { filterDirection = BleLogEntry.Direction.TX },
                    label = { Text("TX App->ESP") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OlloCyan.copy(alpha = 0.2f),
                        selectedLabelColor = OlloCyan
                    )
                )
                FilterChip(
                    selected = filterDirection == BleLogEntry.Direction.RX,
                    onClick = { filterDirection = BleLogEntry.Direction.RX },
                    label = { Text("RX ESP->App") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OlloGreen.copy(alpha = 0.2f),
                        selectedLabelColor = OlloGreen
                    )
                )
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No BLE packets logged yet.\nPackets will appear here during scan, connect, and sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OlloTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        BleLogCard(log)
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BleLogCard(log: BleLogEntry) {
    val isTx = log.direction == BleLogEntry.Direction.TX
    val badgeColor = if (isTx) OlloCyan else OlloGreen
    val badgeBg = if (isTx) OlloCyan.copy(alpha = 0.15f) else OlloGreen.copy(alpha = 0.15f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(OlloDarkSurface)
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isTx) "TX" else "RX",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.typeName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OlloTextPrimary
                )
            }
            Text(
                text = log.formattedTime(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = OlloTextMuted
            )
        }

        if (log.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.note,
                style = MaterialTheme.typography.bodySmall,
                color = OlloTextSecondary
            )
        }

        if (log.hexData.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
                    .padding(6.dp)
            ) {
                Text(
                    text = log.hexData,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = OlloCyan
                )
            }
        }
    }
}
