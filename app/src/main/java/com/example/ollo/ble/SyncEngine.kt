package com.example.ollo.ble

import com.example.ollo.image.ImageStorage
import com.example.ollo.model.CardItem
import com.example.ollo.model.StoredImageMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles the complete incremental sync flow between the app and Ollo AR glasses.
 */
class SyncEngine(
    private val bleManager: BleManager,
    private val imageStorage: ImageStorage
) {
    sealed class SyncStatus {
        data object Idle : SyncStatus()
        data class Syncing(
            val stepDescription: String,
            val progress: Float, // 0.0 to 1.0
            val currentCard: Int,
            val totalCards: Int,
            val currentImage: Int,
            val totalImages: Int,
            val bytesTransferred: Int,
            val skippedImages: Int
        ) : SyncStatus()
        data class Success(
            val cardsSynced: Int,
            val imagesSynced: Int,
            val imagesSkipped: Int,
            val totalBytes: Int,
            val elapsedMs: Long
        ) : SyncStatus()
        data class Failed(val error: String, val canRetry: Boolean = true) : SyncStatus()
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private var cancelRequested = false

    fun cancelSync() {
        cancelRequested = true
    }

    /**
     * Executes the sync flow.
     */
    suspend fun startSync(cards: List<CardItem>): Boolean {
        if (!bleManager.isConnected) {
            _syncStatus.value = SyncStatus.Failed("Glasses are not connected", canRetry = true)
            return false
        }

        cancelRequested = false
        val startTime = System.currentTimeMillis()
        var imagesUploaded = 0
        var imagesSkipped = 0
        var totalBytesTransferred = 0

        try {
            // 1. App sends BEGIN_SYNC, waits for OK (up to 3 retries)
            updateProgress("Starting sync...", 0.05f, 0, cards.size, 0, 0, 0, 0)

            val beginPacket = BleProtocol.buildBeginSync(cards.size)
            val beginOk = sendWithRetry(
                packet = beginPacket,
                expectedRef = BleProtocol.TYPE_BEGIN_SYNC,
                packetName = "BEGIN_SYNC",
                note = "cardCount: ${cards.size}"
            )
            if (!beginOk) {
                _syncStatus.value = SyncStatus.Failed("Glasses rejected BEGIN_SYNC", canRetry = true)
                return false
            }

            // 2. App sends every CARD, waiting for OK after each
            for ((index, card) in cards.withIndex()) {
                if (cancelRequested) throw CancellationException("Sync cancelled by user")

                val cardProgress = 0.05f + (0.35f * (index + 1) / maxOf(1, cards.size))
                updateProgress(
                    stepDescription = "Syncing card ${index + 1} of ${cards.size}",
                    progress = cardProgress,
                    currentCard = index + 1,
                    totalCards = cards.size,
                    currentImage = 0,
                    totalImages = 0,
                    bytesTransferred = totalBytesTransferred,
                    skippedImages = 0
                )

                val cardPacket = BleProtocol.buildCard(
                    index = index,
                    frontImgId = card.frontImgId,
                    backImgId = card.backImgId,
                    frontText = card.frontText,
                    backText = card.backText
                )

                val cardOk = sendWithRetry(
                    packet = cardPacket,
                    expectedRef = BleProtocol.TYPE_CARD,
                    packetName = "CARD #${index}",
                    note = "frontId: ${card.frontImgId}, backId: ${card.backImgId}"
                )
                if (!cardOk) {
                    _syncStatus.value = SyncStatus.Failed("Failed syncing card #${index + 1}", canRetry = true)
                    return false
                }
                totalBytesTransferred += cardPacket.size
            }

            // 3. Collect unique images referenced by any card in the deck
            val uniqueImgIds = mutableSetOf<Long>()
            for (c in cards) {
                if (c.frontImgId != 0L) uniqueImgIds.add(c.frontImgId)
                if (c.backImgId != 0L) uniqueImgIds.add(c.backImgId)
            }

            val imgList = uniqueImgIds.toList()
            val totalImages = imgList.size

            for ((imgIndex, imgId) in imgList.withIndex()) {
                if (cancelRequested) throw CancellationException("Sync cancelled by user")

                val meta: StoredImageMeta? = imageStorage.getMetadata(imgId)
                val pixelData: ByteArray? = imageStorage.loadRawBytes(imgId)

                if (meta == null || pixelData == null) {
                    _syncStatus.value = SyncStatus.Failed("Missing local image data for ID $imgId", canRetry = false)
                    return false
                }

                val imgProgressBase = 0.40f + (0.55f * imgIndex / maxOf(1, totalImages))
                updateProgress(
                    stepDescription = "Checking image ${imgIndex + 1} of $totalImages on glasses...",
                    progress = imgProgressBase,
                    currentCard = cards.size,
                    totalCards = cards.size,
                    currentImage = imgIndex + 1,
                    totalImages = totalImages,
                    bytesTransferred = totalBytesTransferred,
                    skippedImages = imagesSkipped
                )

                // Send IMG_BEGIN and wait for notification
                val imgBeginPacket = BleProtocol.buildImgBegin(
                    imgId = imgId,
                    width = meta.width,
                    height = meta.height,
                    dataLen = pixelData.size
                )

                var response: BleProtocol.NotificationResponse? = null
                for (attempt in 1..3) {
                    bleManager.sendPacket(
                        packet = imgBeginPacket,
                        typeName = "IMG_BEGIN",
                        note = "id: $imgId (${attempt}/3)"
                    )
                    response = bleManager.waitForResponse(BleProtocol.TYPE_IMG_BEGIN, timeoutMs = 3000)
                    if (response != null) break
                    delay(100)
                }

                if (response == null) {
                    _syncStatus.value = SyncStatus.Failed("Timeout waiting for IMG_BEGIN response", canRetry = true)
                    return false
                }

                if (response.isStorageFull) {
                    _syncStatus.value = SyncStatus.Failed("Glasses storage is FULL (status 3)", canRetry = false)
                    return false
                }

                if (response.isError) {
                    _syncStatus.value = SyncStatus.Failed("Glasses returned error for image $imgId", canRetry = true)
                    return false
                }

                // If status == 2 (ALREADY_HAVE_IMG), skip sending chunks!
                if (response.isAlreadyHave) {
                    imagesSkipped++
                    // Also mark in simulator if simulated
                    bleManager.markSimulatedImageKnown(imgId)
                    continue
                }

                // status == 0 (OK) -> stream IMG_CHUNK packets back to back
                imagesUploaded++
                val maxChunkSize = bleManager.maxPayloadSize - BleProtocol.IMG_CHUNK_HEADER_LEN
                val chunkSize = maxChunkSize.coerceIn(32, 235)
                var offset = 0

                while (offset < pixelData.size) {
                    if (cancelRequested) throw CancellationException("Sync cancelled by user")

                    val len = minOf(chunkSize, pixelData.size - offset)
                    val chunkPayload = pixelData.copyOfRange(offset, offset + len)
                    val chunkPacket = BleProtocol.buildImgChunk(offset, chunkPayload)

                    val chunkOk = bleManager.sendPacket(
                        packet = chunkPacket,
                        typeName = "IMG_CHUNK",
                        note = "off: $offset, len: $len"
                    )
                    if (!chunkOk) {
                        _syncStatus.value = SyncStatus.Failed("Failed sending image chunk at offset $offset", canRetry = true)
                        return false
                    }

                    offset += len
                    totalBytesTransferred += chunkPacket.size

                    // Pace slightly between chunks (10-15ms)
                    delay(12)
                }

                // Send IMG_END and wait for OK
                val imgEndPacket = BleProtocol.buildImgEnd(pixelData)
                val imgEndOk = sendWithRetry(
                    packet = imgEndPacket,
                    expectedRef = BleProtocol.TYPE_IMG_END,
                    packetName = "IMG_END",
                    note = "checksum verification"
                )
                if (!imgEndOk) {
                    _syncStatus.value = SyncStatus.Failed("Image checksum verification failed on glasses", canRetry = true)
                    return false
                }
                totalBytesTransferred += imgEndPacket.size

                // Mark image known in simulator
                bleManager.markSimulatedImageKnown(imgId)
            }

            // 4. App sends END_SYNC, waits for OK
            updateProgress(
                stepDescription = "Finalizing sync on glasses...",
                progress = 0.98f,
                currentCard = cards.size,
                totalCards = cards.size,
                currentImage = totalImages,
                totalImages = totalImages,
                bytesTransferred = totalBytesTransferred,
                skippedImages = imagesSkipped
            )

            val endPacket = BleProtocol.buildEndSync()
            val endOk = sendWithRetry(
                packet = endPacket,
                expectedRef = BleProtocol.TYPE_END_SYNC,
                packetName = "END_SYNC",
                note = "Finalize LittleFS"
            )
            if (!endOk) {
                _syncStatus.value = SyncStatus.Failed("Glasses rejected END_SYNC", canRetry = true)
                return false
            }

            val elapsed = System.currentTimeMillis() - startTime
            _syncStatus.value = SyncStatus.Success(
                cardsSynced = cards.size,
                imagesSynced = imagesUploaded,
                imagesSkipped = imagesSkipped,
                totalBytes = totalBytesTransferred,
                elapsedMs = elapsed
            )
            return true

        } catch (_: CancellationException) {
            _syncStatus.value = SyncStatus.Failed("Sync was cancelled", canRetry = true)
            return false
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Failed("Sync failed: ${e.message}", canRetry = true)
            return false
        }
    }

    private suspend fun sendWithRetry(
        packet: ByteArray,
        expectedRef: Byte,
        packetName: String,
        note: String = "",
        maxRetries: Int = 3
    ): Boolean {
        for (attempt in 1..maxRetries) {
            if (cancelRequested) throw CancellationException("Sync cancelled by user")

            val sent = bleManager.sendPacket(packet, packetName, "$note (attempt $attempt/$maxRetries)")
            if (sent) {
                val response = bleManager.waitForResponse(expectedRef, timeoutMs = 3000)
                if (response != null && response.isOk) {
                    return true
                }
            }
            delay(150)
        }
        return false
    }

    private fun updateProgress(
        stepDescription: String,
        progress: Float,
        currentCard: Int,
        totalCards: Int,
        currentImage: Int,
        totalImages: Int,
        bytesTransferred: Int,
        skippedImages: Int
    ) {
        _syncStatus.value = SyncStatus.Syncing(
            stepDescription = stepDescription,
            progress = progress.coerceIn(0f, 1f),
            currentCard = currentCard,
            totalCards = totalCards,
            currentImage = currentImage,
            totalImages = totalImages,
            bytesTransferred = bytesTransferred,
            skippedImages = skippedImages
        )
    }

    fun resetStatus() {
        _syncStatus.value = SyncStatus.Idle
    }
}
