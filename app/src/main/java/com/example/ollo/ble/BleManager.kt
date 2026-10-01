package com.example.ollo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Manages Bluetooth Low Energy (BLE) scanning, connection, MTU negotiation,
 * and transmission for Ollo AR Glasses.
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BleManager"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Scanning : ConnectionState()
        data class Connecting(val deviceName: String, val address: String) : ConnectionState()
        data class Connected(val deviceName: String, val address: String, val mtu: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class DiscoveredDevice(
        val name: String,
        val address: String,
        val rssi: Int
    )

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _incomingNotifications = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingNotifications: SharedFlow<ByteArray> = _incomingNotifications.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private var negotiatedMtu: Int = 23 // Default minimum BLE MTU
    val maxPayloadSize: Int
        get() = (negotiatedMtu - 3).coerceIn(20, BleProtocol.DEFAULT_MAX_WRITE_LEN)

    private var scanJob: Job? = null
    private var isSimulated: Boolean = false

    // Simulation state for testing when hardware BLE is not present
    private val simulatedKnownImages = mutableSetOf<Long>()
    private var simulatedChunkChecksum = 0

    val isConnected: Boolean
        get() = _connectionState.value is ConnectionState.Connected

    /**
     * Check whether BLE hardware is supported on this device.
     */
    fun isBleSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Start scanning for Ollo peripheral.
     */
    fun startScan() {
        if (isSimulated) {
            _connectionState.value = ConnectionState.Scanning
            _discoveredDevices.value = listOf(
                DiscoveredDevice("Ollo Glasses (Virtual ESP32)", "00:11:22:33:44:55", -42)
            )
            return
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.Error("Bluetooth not ready or disabled")
            return
        }

        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleProtocol.SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setDeviceName(BleProtocol.DEVICE_NAME)
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            // Stop scanning automatically after 10 seconds
            scanJob?.cancel()
            scanJob = scope.launch {
                delay(10000)
                stopScan()
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("BLE permission missing: ${e.message}")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Scan failed: ${e.message}")
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (!isSimulated) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: Exception) {}
        }
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: "Ollo"
            val address = device.address
            val rssi = result.rssi

            val current = _discoveredDevices.value.toMutableList()
            if (current.none { it.address == address }) {
                current.add(DiscoveredDevice(name, address, rssi))
                _discoveredDevices.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with code: $errorCode")
            _connectionState.value = ConnectionState.Error("Scan failed code $errorCode")
        }
    }

    /**
     * Connect to a specific BLE device by MAC address or virtual mode.
     */
    fun connect(address: String, name: String = "Ollo", simulated: Boolean = false) {
        stopScan()
        disconnect()

        isSimulated = simulated
        if (simulated) {
            connectSimulated(address, name)
            return
        }

        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Invalid address $address: ${e.message}")
            return
        }

        if (device == null) {
            _connectionState.value = ConnectionState.Error("Device not found")
            return
        }

        _connectionState.value = ConnectionState.Connecting(name, address)

        try {
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Bluetooth Connect permission missing")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Connect error: ${e.message}")
        }
    }

    private fun connectSimulated(address: String, name: String) {
        scope.launch {
            _connectionState.value = ConnectionState.Connecting(name, address)
            delay(500)
            negotiatedMtu = 247
            _connectionState.value = ConnectionState.Connected(name, address, 247)
            BlePacketLogger.logRx(
                byteArrayOf(),
                "VIRTUAL_CONNECT",
                "Connected to Virtual Ollo Glasses simulator (MTU 247)"
            )
        }
    }

    fun disconnect() {
        if (isSimulated) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {}
        activeGatt = null
        writeChar = null
        notifyChar = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            val name = device.name ?: "Ollo"
            val address = device.address

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "GATT connected, requesting MTU 512...")
                // Request large MTU (aiming for 247+)
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT disconnected (status $status, state $newState)")
                scope.launch {
                    _connectionState.value = ConnectionState.Disconnected
                }
                gatt.close()
                activeGatt = null
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu (status: $status)")
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            // Discover services after MTU negotiation
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                scope.launch {
                    _connectionState.value = ConnectionState.Error("Service discovery failed ($status)")
                }
                return
            }

            val service = gatt.getService(BleProtocol.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Ollo Service UUID not found on device!")
                scope.launch {
                    _connectionState.value = ConnectionState.Error("Ollo service not found on device")
                }
                return
            }

            writeChar = service.getCharacteristic(BleProtocol.WRITE_CHAR_UUID)
            notifyChar = service.getCharacteristic(BleProtocol.NOTIFY_CHAR_UUID)

            if (notifyChar != null) {
                enableNotifications(gatt, notifyChar!!)
            }

            val deviceName = gatt.device.name ?: "Ollo"
            val address = gatt.device.address
            scope.launch {
                _connectionState.value = ConnectionState.Connected(deviceName, address, negotiatedMtu)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value ?: return
            handleIncomingNotification(data)
        }

        @Deprecated("Deprecated for newer Android, kept for compat")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingNotification(value)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun handleIncomingNotification(data: ByteArray) {
        val parsed = BleProtocol.parseNotification(data)
        val note = if (parsed != null) {
            "ref: ${parsed.refTypeName()}, status: ${parsed.statusDescription()}"
        } else ""

        BlePacketLogger.logRx(data, "ESP_NOTIFY", note)
        _incomingNotifications.tryEmit(data)
    }

    /**
     * Send packet to the Ollo glasses over Write Without Response.
     */
    suspend fun sendPacket(packet: ByteArray, typeName: String, note: String = ""): Boolean {
        BlePacketLogger.logTx(packet, typeName, note)

        if (isSimulated) {
            return simulateEspResponse(packet)
        }

        val gatt = activeGatt ?: return false
        val characteristic = writeChar ?: return false

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = packet

        return try {
            val success = gatt.writeCharacteristic(characteristic)
            // Pace packets slightly to avoid overflowing peripheral BLE stack
            delay(10)
            success
        } catch (e: Exception) {
            Log.e(TAG, "Write packet failed", e)
            false
        }
    }

    /**
     * Wait for a notification response from the ESP32 corresponding to a specific refType.
     */
    suspend fun waitForResponse(expectedRefType: Byte, timeoutMs: Long = 3000): BleProtocol.NotificationResponse? {
        val result = withTimeoutOrNull(timeoutMs) {
            val deferred = CompletableDeferred<BleProtocol.NotificationResponse?>()
            val job = scope.launch {
                incomingNotifications.collect { data ->
                    val parsed = BleProtocol.parseNotification(data)
                    if (parsed != null && parsed.refType == expectedRefType) {
                        deferred.complete(parsed)
                    }
                }
            }
            val res = deferred.await()
            job.cancel()
            res
        }
        return result
    }

    /**
     * Simulates the exact ESP32 firmware state machine in virtual mode.
     */
    private suspend fun simulateEspResponse(packet: ByteArray): Boolean {
        if (packet.isEmpty()) return true
        val packetType = packet[0]

        // Add realistic simulated BLE transmission delay (5..20ms)
        delay(15)

        when (packetType) {
            BleProtocol.TYPE_BEGIN_SYNC -> {
                // BEGIN_SYNC: ESP initializes LittleFS temporary deck file
                val response = byteArrayOf(BleProtocol.TYPE_NOTIFY_HEADER, BleProtocol.TYPE_BEGIN_SYNC, BleProtocol.STATUS_OK)
                handleIncomingNotification(response)
            }
            BleProtocol.TYPE_CARD -> {
                // CARD: ESP writes card to flash
                val response = byteArrayOf(BleProtocol.TYPE_NOTIFY_HEADER, BleProtocol.TYPE_CARD, BleProtocol.STATUS_OK)
                handleIncomingNotification(response)
            }
            BleProtocol.TYPE_IMG_BEGIN -> {
                // IMG_BEGIN: [0x03][imgId: u32 LE][width: u16 LE][height: u16 LE][dataLen: u32 LE]
                if (packet.size >= 5) {
                    val imgId = (packet[1].toLong() and 0xFF) or
                            ((packet[2].toLong() and 0xFF) shl 8) or
                            ((packet[3].toLong() and 0xFF) shl 16) or
                            ((packet[4].toLong() and 0xFF) shl 24)

                    val status = if (simulatedKnownImages.contains(imgId)) {
                        BleProtocol.STATUS_ALREADY_HAVE_IMG // 2: Skip sending chunks!
                    } else {
                        simulatedChunkChecksum = 0
                        BleProtocol.STATUS_OK // 0: Send chunks!
                    }
                    val response = byteArrayOf(BleProtocol.TYPE_NOTIFY_HEADER, BleProtocol.TYPE_IMG_BEGIN, status)
                    handleIncomingNotification(response)
                }
            }
            BleProtocol.TYPE_IMG_CHUNK -> {
                // IMG_CHUNK: Accumulate checksum, no notification needed for individual chunks
                for (i in 5 until packet.size) {
                    simulatedChunkChecksum = (simulatedChunkChecksum + (packet[i].toInt() and 0xFF)) and 0xFF
                }
            }
            BleProtocol.TYPE_IMG_END -> {
                // IMG_END: verify checksum
                val expectedChecksum = if (packet.size >= 2) packet[1].toInt() and 0xFF else -1
                val status = if (expectedChecksum == simulatedChunkChecksum) {
                    BleProtocol.STATUS_OK
                } else {
                    BleProtocol.STATUS_ERROR
                }
                val response = byteArrayOf(BleProtocol.TYPE_NOTIFY_HEADER, BleProtocol.TYPE_IMG_END, status)
                handleIncomingNotification(response)
            }
            BleProtocol.TYPE_END_SYNC -> {
                // END_SYNC: ESP finalizes deck swap and cleans unreferenced images
                val response = byteArrayOf(BleProtocol.TYPE_NOTIFY_HEADER, BleProtocol.TYPE_END_SYNC, BleProtocol.STATUS_OK)
                handleIncomingNotification(response)
            }
        }
        return true
    }

    fun markSimulatedImageKnown(imgId: Long) {
        simulatedKnownImages.add(imgId)
    }

    fun clearSimulatedImages() {
        simulatedKnownImages.clear()
    }
}
