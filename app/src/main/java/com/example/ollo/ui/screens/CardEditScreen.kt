package com.example.ollo.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ollo.image.ImageProcessing
import com.example.ollo.model.CardItem
import com.example.ollo.ui.viewmodel.OlloViewModel
import com.example.ollo.util.AsciiHelper
import com.example.ui.theme.OlloAmber
import com.example.ui.theme.OlloCyan
import com.example.ui.theme.OlloDarkBg
import com.example.ui.theme.OlloDarkSurface
import com.example.ui.theme.OlloDarkSurfaceCard
import com.example.ui.theme.OlloRed
import com.example.ui.theme.OlloTextMuted
import com.example.ui.theme.OlloTextPrimary
import com.example.ui.theme.OlloTextSecondary
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    card: CardItem,
    viewModel: OlloViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var frontText by remember { mutableStateOf(AsciiHelper.decodeFromWireText(card.frontText)) }
    var backText by remember { mutableStateOf(AsciiHelper.decodeFromWireText(card.backText)) }
    var frontImgId by remember { mutableLongStateOf(card.frontImgId) }
    var backImgId by remember { mutableLongStateOf(card.backImgId) }

    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load initial 1-bit bitmaps if present
    LaunchedEffect(frontImgId) {
        if (frontImgId != 0L) {
            frontBitmap = viewModel.loadImageBitmap(frontImgId)
        } else {
            frontBitmap = null
        }
    }
    LaunchedEffect(backImgId) {
        if (backImgId != 0L) {
            backBitmap = viewModel.loadImageBitmap(backImgId)
        } else {
            backBitmap = null
        }
    }

    // Image picker / camera handling
    var editingTargetSide by remember { mutableStateOf<String?>(null) } // "front" or "back"
    var pendingSourceUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingSourceUri = uri
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            pendingSourceUri = tempCameraUri
        }
    }

    fun launchCamera(side: String) {
        editingTargetSide = side
        val imagesDir = File(context.cacheDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val tempFile = File(imagesDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        tempCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun launchGallery(side: String) {
        editingTargetSide = side
        galleryLauncher.launch("image/*")
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Image editor dialog
    if (pendingSourceUri != null && editingTargetSide != null) {
        ImageEditorDialog(
            sourceUri = pendingSourceUri!!,
            initialDither = settings.defaultDither,
            initialInvert = settings.defaultInvert,
            initialThreshold = settings.defaultThreshold,
            maxW = settings.maxImageWidth,
            maxH = settings.maxImageHeight,
            imageStorage = viewModel.imageStorage,
            onDismiss = {
                pendingSourceUri = null
                editingTargetSide = null
            },
            onConfirm = { processed ->
                scope.launch {
                    val savedId = viewModel.imageStorage.saveImage(processed)
                    if (editingTargetSide == "front") {
                        frontImgId = savedId
                        frontBitmap = viewModel.loadImageBitmap(savedId)
                    } else if (editingTargetSide == "back") {
                        backImgId = savedId
                        backBitmap = viewModel.loadImageBitmap(savedId)
                    }
                    pendingSourceUri = null
                    editingTargetSide = null
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (card.id == 0L) "New Flashcard" else "Edit Flashcard",
                        fontWeight = FontWeight.Bold,
                        color = OlloCyan
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OlloTextPrimary
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveEditingCard(
                                frontUiText = frontText,
                                backUiText = backText,
                                frontImgId = frontImgId,
                                backImgId = backImgId
                            )
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OlloCyan,
                            contentColor = Color(0xFF00363D)
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_card_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OlloDarkBg,
                    titleContentColor = OlloCyan
                )
            )
        },
        containerColor = OlloDarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Specs callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OlloDarkSurfaceCard, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Hardware constraints: 5x7 ASCII bitmap font, max 100 characters per side. Line breaks become literal \\n on wire. Sides may be image-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OlloTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- FRONT SIDE SECTION ---
            CardSection(
                sideTitle = "FRONT SIDE (Question / Prompt)",
                textValue = frontText,
                onTextChange = { frontText = it },
                onSanitize = { frontText = AsciiHelper.sanitizeToAscii(frontText) },
                imgId = frontImgId,
                bitmap = frontBitmap,
                onLaunchCamera = { launchCamera("front") },
                onLaunchGallery = { launchGallery("front") },
                onRemoveImage = {
                    frontImgId = 0L
                    frontBitmap = null
                },
                textTag = "front_text_input",
                cameraTag = "front_camera_button",
                galleryTag = "front_gallery_button"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- BACK SIDE SECTION ---
            CardSection(
                sideTitle = "BACK SIDE (Answer / Details)",
                textValue = backText,
                onTextChange = { backText = it },
                onSanitize = { backText = AsciiHelper.sanitizeToAscii(backText) },
                imgId = backImgId,
                bitmap = backBitmap,
                onLaunchCamera = { launchCamera("back") },
                onLaunchGallery = { launchGallery("back") },
                onRemoveImage = {
                    backImgId = 0L
                    backBitmap = null
                },
                textTag = "back_text_input",
                cameraTag = "back_camera_button",
                galleryTag = "back_gallery_button"
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CardSection(
    sideTitle: String,
    textValue: String,
    onTextChange: (String) -> Unit,
    onSanitize: () -> Unit,
    imgId: Long,
    bitmap: Bitmap?,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onRemoveImage: () -> Unit,
    textTag: String,
    cameraTag: String,
    galleryTag: String
) {
    val wireCandidate = AsciiHelper.encodeToWireText(textValue)
    val charCount = wireCandidate.length
    val hasNonAscii = AsciiHelper.hasNonAscii(textValue)
    val isOverLimit = charCount > AsciiHelper.MAX_CARD_TEXT_LEN

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
                Text(
                    text = sideTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = OlloCyan
                )
                Text(
                    text = "$charCount / 100",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isOverLimit -> OlloRed
                        charCount > 85 -> OlloAmber
                        else -> OlloTextSecondary
                    }
                )
            }

            // Non-ASCII warning banner
            if (hasNonAscii) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(OlloAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = OlloAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Non-ASCII chars will not render on glasses!",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloAmber
                        )
                    }
                    OutlinedButton(
                        onClick = onSanitize,
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OlloAmber)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sanitize", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Text input
            OutlinedTextField(
                value = textValue,
                onValueChange = onTextChange,
                placeholder = { Text("Enter text (or leave blank for image-only)", color = OlloTextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(textTag),
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OlloCyan,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = OlloTextPrimary,
                    unfocusedTextColor = OlloTextPrimary,
                    cursorColor = OlloCyan
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1-Bit Image Area
            Text(
                text = "1-Bit Micro-OLED Image",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = OlloTextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (imgId != 0L && bitmap != null) {
                // Image preview box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, OlloCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "1-bit preview",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${bitmap.width} × ${bitmap.height} px",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = OlloCyan
                        )
                        Text(
                            text = "CRC32 ID: 0x${imgId.toString(16).uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = OlloTextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove image",
                            tint = OlloRed
                        )
                    }
                }
            } else {
                // Empty image picker buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onLaunchGallery,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag(galleryTag),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OlloCyan)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Image", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onLaunchCamera,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag(cameraTag),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OlloCyan)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
