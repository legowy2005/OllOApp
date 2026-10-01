package com.example.ollo.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Ollo BLE Protocol definition matching ESP32 firmware.
 *
 * All multi-byte integers are serialized as Little-Endian.
 */
object BleProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("6f6c6c6f-0001-4000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("6f6c6c6f-0002-4000-8000-00805f9b34fb")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("6f6c6c6f-0003-4000-8000-00805f9b34fb")

    const val DEVICE_NAME = "Ollo"

    // Packet Types: App -> ESP
    const val TYPE_BEGIN_SYNC: Byte = 0x01
    const val TYPE_CARD: Byte = 0x02
    const val TYPE_IMG_BEGIN: Byte = 0x03
    const val TYPE_IMG_CHUNK: Byte = 0x04
    const val TYPE_IMG_END: Byte = 0x05
    const val TYPE_END_SYNC: Byte = 0x06

    // Response header: ESP -> App
    const val TYPE_NOTIFY_HEADER: Byte = 0x80.toByte()

    // Status codes from ESP
    const val STATUS_OK: Byte = 0x00
    const val STATUS_ERROR: Byte = 0x01
    const val STATUS_ALREADY_HAVE_IMG: Byte = 0x02
    const val STATUS_STORAGE_FULL: Byte = 0x03

    // MTU and chunk bounds
    const val DEFAULT_MAX_WRITE_LEN = 240
    const val IMG_CHUNK_HEADER_LEN = 5 // 1 byte type + 4 bytes offset

    /**
     * Builds BEGIN_SYNC packet:
     * [0x01][cardCount: u16 LE]
     */
    fun buildBeginSync(cardCount: Int): ByteArray {
        val buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(TYPE_BEGIN_SYNC)
        buffer.putShort(cardCount.toShort())
        return buffer.array()
    }

    /**
     * Builds CARD packet:
     * [0x02][index: u16 LE][frontImgId: u32 LE][backImgId: u32 LE]
     * [frontLen: u8][backLen: u8][frontText: ASCII][backText: ASCII]
     */
    fun buildCard(
        index: Int,
        frontImgId: Long,
        backImgId: Long,
        frontText: String,
        backText: String
    ): ByteArray {
        val frontBytes = frontText.toByteArray(Charsets.US_ASCII)
        val backBytes = backText.toByteArray(Charsets.US_ASCII)

        require(frontBytes.size <= 100) { "Front text exceeds 100 bytes" }
        require(backBytes.size <= 100) { "Back text exceeds 100 bytes" }

        val totalLen = 1 + 2 + 4 + 4 + 1 + 1 + frontBytes.size + backBytes.size
        val buffer = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(TYPE_CARD)
        buffer.putShort(index.toShort())
        buffer.putInt(frontImgId.toInt())
        buffer.putInt(backImgId.toInt())
        buffer.put(frontBytes.size.toByte())
        buffer.put(backBytes.size.toByte())
        buffer.put(frontBytes)
        buffer.put(backBytes)

        return buffer.array()
    }

    /**
     * Builds IMG_BEGIN packet:
     * [0x03][imgId: u32 LE][width: u16 LE][height: u16 LE][dataLen: u32 LE]
     */
    fun buildImgBegin(
        imgId: Long,
        width: Int,
        height: Int,
        dataLen: Int
    ): ByteArray {
        val buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(TYPE_IMG_BEGIN)
        buffer.putInt(imgId.toInt())
        buffer.putShort(width.toShort())
        buffer.putShort(height.toShort())
        buffer.putInt(dataLen)
        return buffer.array()
    }

    /**
     * Builds IMG_CHUNK packet:
     * [0x04][offset: u32 LE][data bytes...]
     */
    fun buildImgChunk(offset: Int, chunkData: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(1 + 4 + chunkData.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(TYPE_IMG_CHUNK)
        buffer.putInt(offset)
        buffer.put(chunkData)
        return buffer.array()
    }

    /**
     * Builds IMG_END packet:
     * [0x05][checksum: u8 (sum of all pixel data bytes mod 256)]
     */
    fun buildImgEnd(pixelData: ByteArray): ByteArray {
        var sum = 0
        for (b in pixelData) {
            sum = (sum + (b.toInt() and 0xFF)) and 0xFF
        }
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(TYPE_IMG_END)
        buffer.put(sum.toByte())
        return buffer.array()
    }

    /**
     * Builds END_SYNC packet:
     * [0x06]
     */
    fun buildEndSync(): ByteArray {
        return byteArrayOf(TYPE_END_SYNC)
    }

    data class NotificationResponse(
        val refType: Byte,
        val status: Byte
    ) {
        val isOk: Boolean get() = status == STATUS_OK
        val isAlreadyHave: Boolean get() = status == STATUS_ALREADY_HAVE_IMG
        val isStorageFull: Boolean get() = status == STATUS_STORAGE_FULL
        val isError: Boolean get() = status == STATUS_ERROR

        fun statusDescription(): String = when (status) {
            STATUS_OK -> "OK (0)"
            STATUS_ERROR -> "ERROR (1)"
            STATUS_ALREADY_HAVE_IMG -> "ALREADY_HAVE (2)"
            STATUS_STORAGE_FULL -> "STORAGE_FULL (3)"
            else -> "UNKNOWN ($status)"
        }

        fun refTypeName(): String = when (refType) {
            TYPE_BEGIN_SYNC -> "BEGIN_SYNC"
            TYPE_CARD -> "CARD"
            TYPE_IMG_BEGIN -> "IMG_BEGIN"
            TYPE_IMG_CHUNK -> "IMG_CHUNK"
            TYPE_IMG_END -> "IMG_END"
            TYPE_END_SYNC -> "END_SYNC"
            else -> String.format("0x%02X", refType)
        }
    }

    /**
     * Parses an incoming notification packet from the ESP:
     * [0x80][refType][status]
     */
    fun parseNotification(data: ByteArray): NotificationResponse? {
        if (data.size < 3) return null
        if (data[0] != TYPE_NOTIFY_HEADER) return null
        return NotificationResponse(
            refType = data[1],
            status = data[2]
        )
    }

    fun packetTypeName(type: Byte): String = when (type) {
        TYPE_BEGIN_SYNC -> "BEGIN_SYNC (0x01)"
        TYPE_CARD -> "CARD (0x02)"
        TYPE_IMG_BEGIN -> "IMG_BEGIN (0x03)"
        TYPE_IMG_CHUNK -> "IMG_CHUNK (0x04)"
        TYPE_IMG_END -> "IMG_END (0x05)"
        TYPE_END_SYNC -> "END_SYNC (0x06)"
        TYPE_NOTIFY_HEADER -> "NOTIFY (0x80)"
        else -> String.format("0x%02X", type)
    }

    fun toHexString(bytes: ByteArray, maxBytes: Int = 32): String {
        val sb = StringBuilder()
        val limit = minOf(bytes.size, maxBytes)
        for (i in 0 until limit) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        if (bytes.size > maxBytes) {
            sb.append("... (${bytes.size} B)")
        }
        return sb.toString().trim()
    }
}
