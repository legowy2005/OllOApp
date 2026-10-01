package com.example.ollo.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model representing a logged BLE packet for the debug log screen.
 */
data class BleLogEntry(
    val id: Long,
    val timestamp: Long,
    val direction: Direction,
    val typeName: String,
    val hexData: String,
    val byteLength: Int,
    val note: String = ""
) {
    enum class Direction { TX, RX }

    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestamp))
    }
}

/**
 * In-memory circular buffer maintaining the last 100 BLE packets sent/received.
 */
object BlePacketLogger {
    private const val MAX_LOG_ENTRIES = 100
    private var sequenceId = 0L

    private val _logs = MutableStateFlow<List<BleLogEntry>>(emptyList())
    val logs: StateFlow<List<BleLogEntry>> = _logs.asStateFlow()

    @Synchronized
    fun logTx(packet: ByteArray, typeName: String, note: String = "") {
        addEntry(
            BleLogEntry(
                id = ++sequenceId,
                timestamp = System.currentTimeMillis(),
                direction = BleLogEntry.Direction.TX,
                typeName = typeName,
                hexData = BleProtocol.toHexString(packet),
                byteLength = packet.size,
                note = note
            )
        )
    }

    @Synchronized
    fun logRx(packet: ByteArray, typeName: String, note: String = "") {
        addEntry(
            BleLogEntry(
                id = ++sequenceId,
                timestamp = System.currentTimeMillis(),
                direction = BleLogEntry.Direction.RX,
                typeName = typeName,
                hexData = BleProtocol.toHexString(packet),
                byteLength = packet.size,
                note = note
            )
        )
    }

    private fun addEntry(entry: BleLogEntry) {
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > MAX_LOG_ENTRIES) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
    }
}
