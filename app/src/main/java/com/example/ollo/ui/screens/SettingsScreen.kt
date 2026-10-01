package com.example.ollo.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ollo.ble.BleProtocol
import com.example.ollo.ui.viewmodel.OlloViewModel
import com.example.ui.theme.OlloCyan
import com.example.ui.theme.OlloDarkBg
import com.example.ui.theme.OlloDarkSurface
import com.example.ui.theme.OlloDarkSurfaceCard
import com.example.ui.theme.OlloRed
import com.example.ui.theme.OlloTextMuted
import com.example.ui.theme.OlloTextPrimary
import com.example.ui.theme.OlloTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: OlloViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    var ditherDefault by remember(settings) { mutableStateOf(settings.defaultDither) }
    var invertDefault by remember(settings) { mutableStateOf(settings.defaultInvert) }
    var budgetSlider by remember(settings) { mutableFloatStateOf(settings.storageBudgetKb.toFloat()) }
    var selectedRes by remember(settings) {
        mutableStateOf(if (settings.maxImageWidth == 320) "320x240" else "256x192")
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Local Data?", color = OlloRed, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all flashcards and local 1-bit image files from this phone. Glasses flash memory will not be modified until the next sync.", color = OlloTextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OlloRed),
                    modifier = Modifier.testTag("confirm_clear_data_button")
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = OlloTextSecondary)
                }
            },
            containerColor = OlloDarkSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ollo Settings", fontWeight = FontWeight.Bold, color = OlloCyan) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_from_settings")) {
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

            // Image Pipeline Defaults Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1-Bit Image Pipeline Defaults",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OlloCyan
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dither default
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dithering Default", fontWeight = FontWeight.SemiBold, color = OlloTextPrimary)
                            Text("Use Floyd-Steinberg error diffusion", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                        }
                        Switch(
                            checked = ditherDefault,
                            onCheckedChange = {
                                ditherDefault = it
                                viewModel.updateSettings(settings.copy(defaultDither = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = OlloCyan, checkedTrackColor = OlloCyan.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Invert default
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Invert Pixels Default", fontWeight = FontWeight.SemiBold, color = OlloTextPrimary)
                            Text("Invert black/white for dark line drawings", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                        }
                        Switch(
                            checked = invertDefault,
                            onCheckedChange = {
                                invertDefault = it
                                viewModel.updateSettings(settings.copy(defaultInvert = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = OlloCyan, checkedTrackColor = OlloCyan.copy(alpha = 0.3f))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Max image resolution
                    Text("Max Image Downscale Size", fontWeight = FontWeight.SemiBold, color = OlloTextPrimary)
                    Text("Images are scaled to fit without upscaling (RP2040 limit: 320x240)", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedRes == "256x192",
                            onClick = {
                                selectedRes = "256x192"
                                viewModel.updateSettings(settings.copy(maxImageWidth = 256, maxImageHeight = 192))
                            },
                            label = { Text("256 × 192 (Recommended, ~6 KB)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OlloCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OlloCyan
                            )
                        )
                        FilterChip(
                            selected = selectedRes == "320x240",
                            onClick = {
                                selectedRes = "320x240"
                                viewModel.updateSettings(settings.copy(maxImageWidth = 320, maxImageHeight = 240))
                            },
                            label = { Text("320 × 240 (Max, ~9.6 KB)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OlloCyan.copy(alpha = 0.2f),
                                selectedLabelColor = OlloCyan
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device Storage Budget Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Glasses Flash Storage Budget", fontWeight = FontWeight.Bold, color = OlloCyan)
                        Text("${budgetSlider.toInt()} KB", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = OlloCyan)
                    }
                    Text("Warns when the sum of unique images and card text exceeds budget (default 1 MB / 1024 KB)", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = budgetSlider,
                        onValueChange = { budgetSlider = it },
                        onValueChangeFinished = {
                            viewModel.updateSettings(settings.copy(storageBudgetKb = budgetSlider.toInt()))
                        },
                        valueRange = 256f..4096f,
                        steps = 14,
                        colors = SliderDefaults.colors(thumbColor = OlloCyan, activeTrackColor = OlloCyan)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Firmware Developer Reference Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = OlloCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ESP32 Firmware BLE Protocol Spec", fontWeight = FontWeight.Bold, color = OlloCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = """
Service UUID: 
  6f6c6c6f-0001-4000-8000-00805f9b34fb
Write (No Resp): 
  6f6c6c6f-0002-4000-8000-00805f9b34fb
Notify: 
  6f6c6c6f-0003-4000-8000-00805f9b34fb

App -> ESP Packets:
  0x01 BEGIN_SYNC (cardCount: u16 LE)
  0x02 CARD (index: u16, fImg: u32, bImg: u32, fLen: u8, bLen: u8, text...)
  0x03 IMG_BEGIN (imgId: u32, w: u16, h: u16, len: u32)
  0x04 IMG_CHUNK (offset: u32, data...)
  0x05 IMG_END (checksum: u8 mod 256)
  0x06 END_SYNC

ESP -> App: [0x80][refType][status]
  status: 0=OK, 1=Err, 2=Already Have, 3=Full
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = OlloCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danger Zone: Clear Data
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data Management", fontWeight = FontWeight.Bold, color = OlloRed)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Delete all local flashcards and stored 1-bit image files from this device.", style = MaterialTheme.typography.bodySmall, color = OlloTextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showClearDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = OlloRed.copy(alpha = 0.2f), contentColor = OlloRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_all_data_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Local Data", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
