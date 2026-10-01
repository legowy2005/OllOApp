package com.example.ollo.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ollo.ble.BleManager
import com.example.ollo.model.CardItem
import com.example.ollo.ui.viewmodel.OlloViewModel
import com.example.ollo.util.AsciiHelper
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: OlloViewModel,
    onNavigateToEdit: (CardItem) -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val storageEstimate by viewModel.storageEstimate.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    // CSV File Launchers
    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.let { stream ->
                viewModel.importCsv(stream)
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { stream ->
                viewModel.exportCsv(stream)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ollo",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = OlloCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AR Glasses",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloTextSecondary
                        )
                    }
                },
                actions = {
                    // Connection Status Pill
                    val isConnected = connectionState is BleManager.ConnectionState.Connected
                    val pillColor = if (isConnected) OlloGreen else Color(0xFF475569)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(pillColor.copy(alpha = 0.2f))
                            .border(1.dp, pillColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { onNavigateToSync() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pillColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConnected) "Connected" else "Sync",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = pillColor
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onNavigateToLog, modifier = Modifier.testTag("ble_log_nav_button")) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "BLE Log", tint = OlloCyan)
                    }

                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("settings_nav_button")) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = OlloTextSecondary)
                    }

                    IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("more_options_button")) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = OlloTextSecondary)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(OlloDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import CSV (front,back)", color = OlloTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null, tint = OlloCyan) },
                            onClick = {
                                showMenu = false
                                importCsvLauncher.launch("*/*")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export CSV", color = OlloTextPrimary) },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = OlloCyan) },
                            onClick = {
                                showMenu = false
                                exportCsvLauncher.launch("ollo_deck_${System.currentTimeMillis()}.csv")
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OlloDarkBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.startNewCard()
                    onNavigateToEdit(CardItem())
                },
                containerColor = OlloCyan,
                contentColor = Color(0xFF00363D),
                modifier = Modifier.testTag("add_card_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Flashcard")
            }
        },
        containerColor = OlloDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Storage Estimate Dashboard Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Device Storage Estimate",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = OlloCyan
                            )
                            Text(
                                text = "${storageEstimate.cardCount} cards • ${storageEstimate.uniqueImageCount} unique 1-bit images",
                                style = MaterialTheme.typography.bodySmall,
                                color = OlloTextSecondary
                            )
                        }

                        Text(
                            text = "${storageEstimate.totalEstimatedBytes / 1024} KB / ${storageEstimate.budgetBytes / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (storageEstimate.isOverBudget) OlloRed else OlloCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { storageEstimate.usageRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (storageEstimate.isOverBudget) OlloRed else OlloCyan,
                        trackColor = Color(0xFF263238)
                    )

                    if (storageEstimate.isOverBudget) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = OlloRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Over budget! Remove cards or shrink images to prevent ESP32 LittleFS overflow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OlloRed
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search cards...", color = OlloTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = OlloCyan) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("card_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OlloCyan,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = OlloTextPrimary,
                    unfocusedTextColor = OlloTextPrimary,
                    cursorColor = OlloCyan
                )
            )

            // Cards List
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(OlloCyan.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = OlloCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Flashcards in Deck" else "No matching cards",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OlloTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isBlank())
                                "Tap '+' below to create your first card with text & 1-bit OLED image, or import a CSV deck."
                            else
                                "Try searching for a different phrase.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        CardListItem(
                            card = card,
                            viewModel = viewModel,
                            onEdit = {
                                viewModel.editCard(card)
                                onNavigateToEdit(card)
                            },
                            onDelete = { viewModel.deleteCard(card) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Leave room for FAB
                    }
                }
            }
        }
    }
}

@Composable
private fun CardListItem(
    card: CardItem,
    viewModel: OlloViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(card.frontImgId) {
        if (card.frontImgId != 0L) {
            frontBitmap = viewModel.loadImageBitmap(card.frontImgId)
        }
    }
    LaunchedEffect(card.backImgId) {
        if (card.backImgId != 0L) {
            backBitmap = viewModel.loadImageBitmap(card.backImgId)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = OlloDarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Front & Back side previews
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Front Side
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FRONT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OlloCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AsciiHelper.decodeFromWireText(card.frontText).ifBlank { "(image-only)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OlloTextPrimary,
                        maxLines = 2
                    )
                    if (card.hasFrontImage && frontBitmap != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Image(
                            bitmap = frontBitmap!!.asImageBitmap(),
                            contentDescription = "Front 1-bit image",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Back Side
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BACK",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OlloTextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = AsciiHelper.decodeFromWireText(card.backText).ifBlank { "(image-only)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OlloTextSecondary,
                        maxLines = 2
                    )
                    if (card.hasBackImage && backBitmap != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Image(
                            bitmap = backBitmap!!.asImageBitmap(),
                            contentDescription = "Back 1-bit image",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                        )
                    }
                }

                // Action buttons
                Column(verticalArrangement = Arrangement.Center) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit card", tint = OlloCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete card", tint = OlloRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
