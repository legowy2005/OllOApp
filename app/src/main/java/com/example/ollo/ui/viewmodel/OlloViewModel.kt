package com.example.ollo.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ollo.ble.BleManager
import com.example.ollo.ble.BlePacketLogger
import com.example.ollo.ble.SyncEngine
import com.example.ollo.data.AppDatabase
import com.example.ollo.data.CardRepository
import com.example.ollo.data.StorageEstimate
import com.example.ollo.image.ImageProcessing
import com.example.ollo.image.ImageStorage
import com.example.ollo.model.CardItem
import com.example.ollo.model.OlloSettings
import com.example.ollo.util.AsciiHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/**
 * Main ViewModel for Ollo AR Glasses companion app.
 */
class OlloViewModel(application: Application) : AndroidViewModel(application) {

    val imageStorage = ImageStorage(application.applicationContext)
    private val database = AppDatabase.getInstance(application.applicationContext)
    val repository = CardRepository(application.applicationContext, database.cardDao(), imageStorage)

    val bleManager = BleManager(application.applicationContext, viewModelScope)
    val syncEngine = SyncEngine(bleManager, imageStorage)

    val settings: StateFlow<OlloSettings> = repository.settings
    val connectionState = bleManager.connectionState
    val discoveredDevices = bleManager.discoveredDevices
    val syncStatus = syncEngine.syncStatus
    val bleLogs = BlePacketLogger.logs

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Cards list reactive to search query
    val cards: StateFlow<List<CardItem>> = combine(
        repository.getAllCards(),
        _searchQuery
    ) { allCards, query ->
        if (query.isBlank()) {
            allCards
        } else {
            val q = query.trim().lowercase()
            allCards.filter {
                it.frontText.lowercase().contains(q) || it.backText.lowercase().contains(q)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _storageEstimate = MutableStateFlow(
        StorageEstimate(0, 0, 0, 0, 0, 1024 * 1024)
    )
    val storageEstimate: StateFlow<StorageEstimate> = _storageEstimate.asStateFlow()

    // Card currently being edited
    private val _editingCard = MutableStateFlow<CardItem?>(null)
    val editingCard: StateFlow<CardItem?> = _editingCard.asStateFlow()

    // Status snackbar / toast message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        refreshStorageEstimate()

        // Auto-reconnect if last device is remembered
        viewModelScope.launch {
            val s = repository.settings.value
            if (s.lastDeviceAddress.isNotBlank()) {
                if (s.virtualPeripheralEnabled) {
                    bleManager.connect(s.lastDeviceAddress, s.lastDeviceName.ifBlank { "Ollo Virtual" }, simulated = true)
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun refreshStorageEstimate() {
        viewModelScope.launch {
            _storageEstimate.value = repository.computeStorageEstimate()
        }
    }

    fun startNewCard() {
        _editingCard.value = CardItem()
    }

    fun editCard(card: CardItem) {
        _editingCard.value = card
    }

    fun cancelEditing() {
        _editingCard.value = null
    }

    fun saveEditingCard(
        frontUiText: String,
        backUiText: String,
        frontImgId: Long,
        backImgId: Long
    ) {
        val current = _editingCard.value ?: return

        // Text max 100 characters per side; newlines encoded as \n literal
        val wireFront = AsciiHelper.encodeToWireText(frontUiText)
        val wireBack = AsciiHelper.encodeToWireText(backUiText)

        viewModelScope.launch {
            val updated = current.copy(
                frontText = wireFront,
                backText = wireBack,
                frontImgId = frontImgId,
                backImgId = backImgId
            )
            repository.saveCard(updated)
            _editingCard.value = null
            refreshStorageEstimate()
            showMessage("Card saved")
        }
    }

    fun deleteCard(card: CardItem) {
        viewModelScope.launch {
            repository.deleteCard(card)
            refreshStorageEstimate()
            showMessage("Card deleted")
        }
    }

    /**
     * Process an image from Uri into the 1-bit wire format and save to disk.
     */
    suspend fun processAndStoreImage(
        uri: Uri,
        dither: Boolean,
        invert: Boolean,
        threshold: Int
    ): Long? {
        val s = settings.value
        val decoded = imageStorage.decodeAndScaleUri(
            uri = uri,
            maxW = s.maxImageWidth,
            maxH = s.maxImageHeight
        ) ?: return null

        val (pixels, dims) = decoded
        val (w, h) = dims

        return try {
            val processed = ImageProcessing.processArgb(
                argbPixels = pixels,
                width = w,
                height = h,
                dither = dither,
                invert = invert,
                threshold = threshold
            )
            imageStorage.saveImage(processed)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadImageBitmap(imgId: Long): Bitmap? {
        return imageStorage.loadAsBitmap(imgId)
    }

    // BLE scanning and connection
    fun startScan() {
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connectDevice(address: String, name: String, simulated: Boolean = false) {
        repository.updateSettings(
            settings.value.copy(
                lastDeviceAddress = address,
                lastDeviceName = name,
                virtualPeripheralEnabled = simulated
            )
        )
        bleManager.connect(address, name, simulated)
    }

    fun disconnectDevice() {
        bleManager.disconnect()
    }

    fun toggleVirtualMode(enable: Boolean) {
        repository.updateSettings(settings.value.copy(virtualPeripheralEnabled = enable))
        if (enable) {
            bleManager.connect("00:11:22:33:44:55", "Ollo Glasses (Virtual ESP32)", simulated = true)
        } else {
            bleManager.disconnect()
        }
    }

    // Sync flow
    fun startSync() {
        viewModelScope.launch {
            val allCards = database.cardDao().getAllCardsSync().map { it.toDomain() }
            if (allCards.isEmpty()) {
                showMessage("Deck is empty. Add cards first.")
                return@launch
            }
            syncEngine.startSync(allCards)
        }
    }

    fun cancelSync() {
        syncEngine.cancelSync()
    }

    fun resetSyncStatus() {
        syncEngine.resetStatus()
    }

    // CSV Import / Export
    fun exportCsv(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                val count = repository.exportCsv(outputStream)
                showMessage("Exported $count cards to CSV")
            } catch (e: Exception) {
                showMessage("Export failed: ${e.message}")
            }
        }
    }

    fun importCsv(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val count = repository.importCsv(inputStream)
                refreshStorageEstimate()
                showMessage("Imported $count cards from CSV")
            } catch (e: Exception) {
                showMessage("Import failed: ${e.message}")
            }
        }
    }

    fun updateSettings(newSettings: OlloSettings) {
        repository.updateSettings(newSettings)
        refreshStorageEstimate()
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            refreshStorageEstimate()
            showMessage("All local cards and images cleared")
        }
    }

    fun clearBleLogs() {
        BlePacketLogger.clear()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
