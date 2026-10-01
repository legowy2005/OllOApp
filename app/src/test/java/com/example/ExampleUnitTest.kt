package com.example

import com.example.ollo.ble.BleProtocol
import com.example.ollo.image.ImageProcessing
import com.example.ollo.util.AsciiHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun testAsciiHelper() {
        val accented = "Café résumé — 100% 🚀"
        assertTrue(AsciiHelper.hasNonAscii(accented))

        val sanitized = AsciiHelper.sanitizeToAscii(accented)
        assertFalse(AsciiHelper.hasNonAscii(sanitized))
        assertTrue(sanitized.contains("Cafe resume"))

        // Test newline encoding as literal \n
        val multiline = "Line 1\nLine 2\r\nLine 3"
        val wire = AsciiHelper.encodeToWireText(multiline)
        assertTrue(wire.contains("\\n"))
        assertFalse(wire.contains("\n"))

        val decoded = AsciiHelper.decodeFromWireText(wire)
        assertEquals("Line 1\nLine 2\nLine 3", decoded)
    }

    @Test
    fun testFitDimensionsNeverUpscales() {
        // Smaller than max -> keeps original dimensions
        val (w1, h1) = ImageProcessing.computeFitDimensions(100, 80, 256, 192)
        assertEquals(100, w1)
        assertEquals(80, h1)

        // Larger than max -> scales to fit inside aspect ratio
        val (w2, h2) = ImageProcessing.computeFitDimensions(512, 384, 256, 192)
        assertEquals(256, w2)
        assertEquals(192, h2)
    }

    @Test
    fun testImageProcessing1BitPacking() {
        // 8x2 pure black image
        val blackPixels = IntArray(16) { 0 }
        val packedBlack = ImageProcessing.convertTo1Bit(blackPixels, 8, 2, dither = false, invert = false)
        assertEquals(2, packedBlack.size) // 1 byte per row * 2 rows
        assertEquals(0.toByte(), packedBlack[0])
        assertEquals(0.toByte(), packedBlack[1])

        // Inverted: black becomes lit
        val packedInverted = ImageProcessing.convertTo1Bit(blackPixels, 8, 2, dither = false, invert = true)
        assertEquals(0xFF.toByte(), packedInverted[0])
        assertEquals(0xFF.toByte(), packedInverted[1])

        // CRC32 calculation
        val id = ImageProcessing.computeImageId(8, 2, packedInverted)
        assertTrue(id > 0)
    }

    @Test
    fun testBleProtocolBeginSync() {
        val packet = BleProtocol.buildBeginSync(42)
        assertEquals(3, packet.size)
        assertEquals(BleProtocol.TYPE_BEGIN_SYNC, packet[0])

        val buffer = ByteBuffer.wrap(packet, 1, 2).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(42.toShort(), buffer.short)
    }

    @Test
    fun testBleProtocolCard() {
        val packet = BleProtocol.buildCard(
            index = 1,
            frontImgId = 0x12345678L,
            backImgId = 0x0L,
            frontText = "Front question",
            backText = "Back answer"
        )

        assertEquals(BleProtocol.TYPE_CARD, packet[0])
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.get() // type
        assertEquals(1.toShort(), buf.short) // index
        assertEquals(0x12345678.toInt(), buf.int) // frontImgId
        assertEquals(0, buf.int) // backImgId
        val fLen = buf.get().toInt() and 0xFF
        val bLen = buf.get().toInt() and 0xFF
        assertEquals("Front question".length, fLen)
        assertEquals("Back answer".length, bLen)
    }

    @Test
    fun testBleProtocolNotifyParsing() {
        val notifyData = byteArrayOf(0x80.toByte(), 0x03.toByte(), 0x02.toByte())
        val parsed = BleProtocol.parseNotification(notifyData)
        assertNotNull(parsed)
        assertEquals(BleProtocol.TYPE_IMG_BEGIN, parsed!!.refType)
        assertEquals(BleProtocol.STATUS_ALREADY_HAVE_IMG, parsed.status)
        assertTrue(parsed.isAlreadyHave)
    }

    @Test
    fun testImgEndChecksum() {
        val data = byteArrayOf(10, 20, 30, 40)
        val endPacket = BleProtocol.buildImgEnd(data)
        assertEquals(2, endPacket.size)
        assertEquals(BleProtocol.TYPE_IMG_END, endPacket[0])
        assertEquals(100.toByte(), endPacket[1]) // 10+20+30+40 = 100
    }
}
