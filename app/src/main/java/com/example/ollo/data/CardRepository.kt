package com.example.ollo.data

import android.content.Context
import com.example.ollo.image.ImageStorage
import com.example.ollo.model.CardItem
import com.example.ollo.model.OlloSettings
import com.example.ollo.util.AsciiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class StorageEstimate(
    val cardCount: Int,
    val uniqueImageCount: Int,
    val totalTextBytes: Int,
    val totalImageBytes: Int,
    val totalEstimatedBytes: Int,
    val budgetBytes: Int
) {
    val usageRatio: Float
        get() = if (budgetBytes > 0) totalEstimatedBytes.toFloat() / budgetBytes else 0f

    val isOverBudget: Boolean
        get() = totalEstimatedBytes > budgetBytes

    val usagePercentage: Int
        get() = (usageRatio * 100).toInt()
}

class CardRepository(
    private val context: Context,
    private val cardDao: CardDao,
    val imageStorage: ImageStorage
) {
    private val prefs = context.getSharedPreferences("ollo_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<OlloSettings> = _settings.asStateFlow()

    private fun loadSettings(): OlloSettings {
        return OlloSettings(
            defaultDither = prefs.getBoolean("default_dither", true),
            defaultInvert = prefs.getBoolean("default_invert", false),
            defaultThreshold = prefs.getInt("default_threshold", 128),
            maxImageWidth = prefs.getInt("max_img_w", 256),
            maxImageHeight = prefs.getInt("max_img_h", 192),
            storageBudgetKb = prefs.getInt("storage_budget_kb", 1024),
            lastDeviceAddress = prefs.getString("last_device_addr", "") ?: "",
            lastDeviceName = prefs.getString("last_device_name", "") ?: "",
            virtualPeripheralEnabled = prefs.getBoolean("virtual_peripheral", false)
        )
    }

    fun updateSettings(newSettings: OlloSettings) {
        prefs.edit()
            .putBoolean("default_dither", newSettings.defaultDither)
            .putBoolean("default_invert", newSettings.defaultInvert)
            .putInt("default_threshold", newSettings.defaultThreshold)
            .putInt("max_img_w", newSettings.maxImageWidth)
            .putInt("max_img_h", newSettings.maxImageHeight)
            .putInt("storage_budget_kb", newSettings.storageBudgetKb)
            .putString("last_device_addr", newSettings.lastDeviceAddress)
            .putString("last_device_name", newSettings.lastDeviceName)
            .putBoolean("virtual_peripheral", newSettings.virtualPeripheralEnabled)
            .apply()
        _settings.value = newSettings
    }

    fun getAllCards(): Flow<List<CardItem>> {
        return cardDao.getAllCards().map { list -> list.map { it.toDomain() } }
    }

    fun searchCards(query: String): Flow<List<CardItem>> {
        return cardDao.searchCards(query).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getCardById(id: Long): CardItem? {
        return cardDao.getCardById(id)?.toDomain()
    }

    suspend fun saveCard(card: CardItem): Long = withContext(Dispatchers.IO) {
        val entity = CardEntity.fromDomain(card).copy(updatedAt = System.currentTimeMillis())
        if (card.id == 0L) {
            cardDao.insertCard(entity)
        } else {
            cardDao.updateCard(entity)
            card.id
        }
    }

    suspend fun deleteCard(card: CardItem) = withContext(Dispatchers.IO) {
        cardDao.deleteCardById(card.id)
        // Clean up images that are no longer referenced
        val referenced = cardDao.getAllReferencedImageIds().toSet()
        imageStorage.pruneUnusedImages(referenced)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        cardDao.deleteAllCards()
        imageStorage.pruneUnusedImages(emptySet())
    }

    /**
     * Calculates the device storage estimate according to hardware requirements.
     */
    suspend fun computeStorageEstimate(): StorageEstimate = withContext(Dispatchers.IO) {
        val cards = cardDao.getAllCardsSync()
        val budgetBytes = _settings.value.storageBudgetKb * 1024

        var textBytes = 0
        val uniqueImgIds = mutableSetOf<Long>()

        for (c in cards) {
            val frontAscii = c.frontText.toByteArray(Charsets.US_ASCII).size
            val backAscii = c.backText.toByteArray(Charsets.US_ASCII).size
            textBytes += 13 + frontAscii + backAscii // 13 bytes protocol overhead + text bytes
            if (c.frontImgId != 0L) uniqueImgIds.add(c.frontImgId)
            if (c.backImgId != 0L) uniqueImgIds.add(c.backImgId)
        }

        var imageBytes = 0
        for (imgId in uniqueImgIds) {
            val meta = imageStorage.getMetadata(imgId)
            if (meta != null) {
                imageBytes += meta.byteSize
            }
        }

        StorageEstimate(
            cardCount = cards.size,
            uniqueImageCount = uniqueImgIds.size,
            totalTextBytes = textBytes,
            totalImageBytes = imageBytes,
            totalEstimatedBytes = textBytes + imageBytes,
            budgetBytes = budgetBytes
        )
    }

    /**
     * Exports deck as CSV with columns: front,back
     */
    suspend fun exportCsv(outputStream: OutputStream): Int = withContext(Dispatchers.IO) {
        val cards = cardDao.getAllCardsSync()
        outputStream.bufferedWriter().use { writer ->
            writer.write("front,back\n")
            for (c in cards) {
                val escapedFront = escapeCsvField(c.frontText)
                val escapedBack = escapeCsvField(c.backText)
                writer.write("$escapedFront,$escapedBack\n")
            }
        }
        cards.size
    }

    /**
     * Imports deck from CSV stream.
     */
    suspend fun importCsv(inputStream: InputStream): Int = withContext(Dispatchers.IO) {
        val entities = mutableListOf<CardEntity>()
        inputStream.bufferedReader().useLines { lines ->
            var firstLine = true
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (firstLine && (line.startsWith("front,back", ignoreCase = true) || line.startsWith("\"front\",\"back\"", ignoreCase = true))) {
                    firstLine = false
                    continue
                }
                firstLine = false

                val fields = parseCsvLine(line)
                if (fields.isNotEmpty()) {
                    val front = AsciiHelper.encodeToWireText(fields[0])
                    val back = if (fields.size > 1) AsciiHelper.encodeToWireText(fields[1]) else ""
                    entities.add(
                        CardEntity(
                            frontText = front,
                            backText = back,
                            frontImgId = 0L,
                            backImgId = 0L
                        )
                    )
                }
            }
        }
        if (entities.isNotEmpty()) {
            cardDao.insertCards(entities)
        }
        entities.size
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }
}
