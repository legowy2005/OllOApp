package com.example.ollo.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ollo.image.ImageProcessing
import com.example.ollo.image.ImageStorage
import com.example.ui.theme.OlloCyan
import com.example.ui.theme.OlloDarkBg
import com.example.ui.theme.OlloDarkSurfaceCard
import com.example.ui.theme.OlloTextMuted
import com.example.ui.theme.OlloTextPrimary
import com.example.ui.theme.OlloTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImageEditorDialog(
    sourceUri: Uri,
    initialDither: Boolean,
    initialInvert: Boolean,
    initialThreshold: Int,
    maxW: Int,
    maxH: Int,
    imageStorage: ImageStorage,
    onDismiss: () -> Unit,
    onConfirm: (processed: ImageProcessing.ProcessedImage) -> Unit
) {
    var dither by remember { mutableStateOf(initialDither) }
    var invert by remember { mutableStateOf(initialInvert) }
    var threshold by remember { mutableFloatStateOf(initialThreshold.toFloat()) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedImage by remember { mutableStateOf<ImageProcessing.ProcessedImage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var rawDecodedPixels by remember { mutableStateOf<Pair<IntArray, Pair<Int, Int>>?>(null) }
    val scope = rememberCoroutineScope()

    // 1. Initial decode & scale from Uri
    LaunchedEffect(sourceUri) {
        isLoading = true
        val decoded = imageStorage.decodeAndScaleUri(sourceUri, maxW, maxH)
        if (decoded == null) {
            errorMessage = "Failed to decode image"
            isLoading = false
            return@LaunchedEffect
        }
        rawDecodedPixels = decoded
        isLoading = false
    }

    // 2. Re-process 1-bit data when parameters change
    LaunchedEffect(rawDecodedPixels, dither, invert, threshold) {
        val decoded = rawDecodedPixels ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            try {
                val (pixels, dims) = decoded
                val (w, h) = dims
                val processed = ImageProcessing.processArgb(
                    argbPixels = pixels,
                    width = w,
                    height = h,
                    dither = dither,
                    invert = invert,
                    threshold = threshold.toInt()
                )
                processedImage = processed

                // Generate displayable bitmap for live preview
                val argb = ImageProcessing.unpackToArgb(processed.byteData, w, h)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.setPixels(argb, 0, w, 0, 0, w, h)
                previewBitmap = bmp
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OlloCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = OlloDarkBg)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "1-Bit OLED Image Pipeline",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OlloCyan
                        )
                        Text(
                            text = "Pre-processing for RP2040 & Micro-OLED",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live 1-bit OLED Preview Window
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = OlloCyan, modifier = Modifier.size(36.dp))
                    } else if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Live 1-bit OLED preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(previewBitmap!!.width.toFloat() / previewBitmap!!.height.toFloat())
                        )
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Specs metadata bar
                if (processedImage != null) {
                    val p = processedImage!!
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OlloDarkSurfaceCard, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${p.width} × ${p.height} px",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = OlloCyan
                        )
                        Text(
                            text = "${p.totalBytes} B / 10 KB",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (p.totalBytes <= 10240) OlloTextSecondary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "ID: 0x${p.id.toString(16).uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = OlloTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pipeline Controls
                // 1. Floyd-Steinberg Dithering
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Floyd-Steinberg Dithering",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OlloTextPrimary
                        )
                        Text(
                            text = "Simulates shades using error diffusion",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloTextSecondary
                        )
                    }
                    Switch(
                        checked = dither,
                        onCheckedChange = { dither = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OlloCyan,
                            checkedTrackColor = OlloCyan.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("dither_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Invert Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Invert Pixels",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OlloTextPrimary
                        )
                        Text(
                            text = "Invert lit (white) and unlit (black) pixels",
                            style = MaterialTheme.typography.bodySmall,
                            color = OlloTextSecondary
                        )
                    }
                    Switch(
                        checked = invert,
                        onCheckedChange = { invert = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OlloCyan,
                            checkedTrackColor = OlloCyan.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("invert_switch")
                    )
                }

                // 3. Threshold Slider (when dithering is disabled)
                if (!dither) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Binarization Threshold",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OlloTextPrimary
                            )
                            Text(
                                text = "${threshold.toInt()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = OlloCyan
                            )
                        }
                        Slider(
                            value = threshold,
                            onValueChange = { threshold = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = OlloCyan,
                                activeTrackColor = OlloCyan
                            ),
                            modifier = Modifier.testTag("threshold_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_image_edit")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val p = processedImage
                            if (p != null) {
                                onConfirm(p)
                            }
                        },
                        enabled = processedImage != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OlloCyan,
                            contentColor = Color(0xFF00363D)
                        ),
                        modifier = Modifier.testTag("confirm_image_edit")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply to Card", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
