package com.example.ollo.image

import java.util.zip.CRC32
import kotlin.math.min

/**
 * Pure Kotlin image conversion pipeline for the Ollo AR glasses micro-OLED display.
 *
 * Hardware specs:
 * - Micro-OLED: 1-bit black/white.
 * - Maximum buffer on RP2040: 320x240 pixels.
 * - Default max size: 256x192 pixels.
 * - Wire format: rows of ceil(width / 8) bytes, MSB first. Bit 1 = lit pixel (white on OLED).
 * - Hard cap: 10 KB (10,240 bytes) per image.
 * - Image ID: CRC32 of (width u16 LE + height u16 LE + pixelData), if 0 return 1.
 */
object ImageProcessing {

    const val MAX_IMAGE_BYTES = 10 * 1024 // 10 KB hard cap

    /**
     * Represents the processed 1-bit image ready for storage and BLE transmission.
     */
    data class ProcessedImage(
        val id: Long,
        val width: Int,
        val height: Int,
        val rowBytes: Int,
        val byteData: ByteArray
    ) {
        val totalBytes: Int get() = byteData.size

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ProcessedImage) return false
            return id == other.id &&
                    width == other.width &&
                    height == other.height &&
                    byteData.contentEquals(other.byteData)
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + byteData.contentHashCode()
            return result
        }
    }

    /**
     * Compute new dimensions fitting within maxW x maxH without upscaling.
     */
    fun computeFitDimensions(origW: Int, origH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        if (origW <= 0 || origH <= 0) return Pair(1, 1)
        if (origW <= maxW && origH <= maxH) {
            return Pair(origW, origH) // Never upscale
        }
        val scaleW = maxW.toFloat() / origW
        val scaleH = maxH.toFloat() / origH
        val scale = min(scaleW, scaleH)
        val newW = (origW * scale).toInt().coerceAtLeast(1)
        val newH = (origH * scale).toInt().coerceAtLeast(1)
        return Pair(newW, newH)
    }

    /**
     * Converts an RGBA pixel array to 8-bit grayscale [0..255],
     * flattening any transparency onto pure white (255).
     *
     * Standard perceptual luminance formula: Y = 0.299*R + 0.587*G + 0.114*B
     */
    fun rgbaToGrayscale(
        argbPixels: IntArray,
        width: Int,
        height: Int
    ): IntArray {
        val gray = IntArray(width * height)
        for (i in argbPixels.indices) {
            val pixel = argbPixels[i]
            val a = (pixel ushr 24) and 0xFF
            var r = (pixel ushr 16) and 0xFF
            var g = (pixel ushr 8) and 0xFF
            var b = pixel and 0xFF

            // Flatten transparency onto solid white
            if (a < 255) {
                r = (r * a + 255 * (255 - a)) / 255
                g = (g * a + 255 * (255 - a)) / 255
                b = (b * a + 255 * (255 - a)) / 255
            }

            val luma = (299 * r + 587 * g + 114 * b) / 1000
            gray[i] = luma.coerceIn(0, 255)
        }
        return gray
    }

    /**
     * Converts an 8-bit grayscale image to a 1-bit packed byte array.
     *
     * @param gray 8-bit luminance values [0..255].
     * @param width Image width in pixels.
     * @param height Image height in pixels.
     * @param dither If true, uses Floyd-Steinberg error diffusion.
     * @param invert If true, inverts the output bits (e.g. black becomes lit pixel).
     * @param threshold Threshold value [0..255] used when dither is false.
     *
     * @return Packed 1-bit byte array: rows of ceil(width / 8) bytes, MSB first, bit 1 = lit pixel.
     */
    fun convertTo1Bit(
        gray: IntArray,
        width: Int,
        height: Int,
        dither: Boolean = true,
        invert: Boolean = false,
        threshold: Int = 128
    ): ByteArray {
        val rowBytes = (width + 7) / 8
        val totalBytes = rowBytes * height
        val packed = ByteArray(totalBytes)

        if (dither) {
            // Floyd-Steinberg dithering with error diffusion in fixed-point / integer arithmetic
            // We use an error buffer of width * height to avoid mutating the original
            val buffer = IntArray(width * height) { gray[it] }

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val currentVal = buffer[idx].coerceIn(0, 255)

                    // Determine binary decision:
                    // Standard: bright pixel (> 127) -> lit (1), dark pixel -> off (0)
                    // If inverted: bright pixel -> off (0), dark pixel -> lit (1)
                    val isBright = currentVal >= 128
                    val lit = if (invert) !isBright else isBright
                    val targetVal = if (isBright) 255 else 0
                    val error = currentVal - targetVal

                    if (lit) {
                        val byteIndex = y * rowBytes + (x / 8)
                        val bitOffset = 7 - (x % 8)
                        packed[byteIndex] = (packed[byteIndex].toInt() or (1 shl bitOffset)).toByte()
                    }

                    // Diffuse error to neighboring pixels:
                    //       [P]   7/16
                    // 3/16  5/16  1/16
                    if (x + 1 < width) {
                        buffer[idx + 1] += (error * 7) / 16
                    }
                    if (y + 1 < height) {
                        if (x - 1 >= 0) {
                            buffer[(y + 1) * width + (x - 1)] += (error * 3) / 16
                        }
                        buffer[(y + 1) * width + x] += (error * 5) / 16
                        if (x + 1 < width) {
                            buffer[(y + 1) * width + (x + 1)] += (error * 1) / 16
                        }
                    }
                }
            }
        } else {
            // Simple thresholding
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    val luma = gray[idx]
                    val isAbove = luma >= threshold
                    val lit = if (invert) !isAbove else isAbove

                    if (lit) {
                        val byteIndex = y * rowBytes + (x / 8)
                        val bitOffset = 7 - (x % 8)
                        packed[byteIndex] = (packed[byteIndex].toInt() or (1 shl bitOffset)).toByte()
                    }
                }
            }
        }

        return packed
    }

    /**
     * Computes the CRC32 Image ID according to hardware specification:
     * CRC32 of (width u16 LE + height u16 LE + pixel data) as an unsigned 32-bit value.
     * If result == 0, returns 1.
     */
    fun computeImageId(width: Int, height: Int, pixelData: ByteArray): Long {
        val crc = CRC32()
        // width u16 Little-Endian
        crc.update(width and 0xFF)
        crc.update((width ushr 8) and 0xFF)
        // height u16 Little-Endian
        crc.update(height and 0xFF)
        crc.update((height ushr 8) and 0xFF)
        // pixel data
        crc.update(pixelData, 0, pixelData.size)

        var id = crc.value and 0xFFFFFFFFL
        if (id == 0L) {
            id = 1L
        }
        return id
    }

    /**
     * Complete pipeline from ARGB pixel buffer to final ProcessedImage.
     * Enforces the 10 KB hard cap.
     */
    fun processArgb(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        dither: Boolean = true,
        invert: Boolean = false,
        threshold: Int = 128
    ): ProcessedImage {
        val rowBytes = (width + 7) / 8
        val totalBytes = rowBytes * height
        require(totalBytes <= MAX_IMAGE_BYTES) {
            "Image size ($totalBytes bytes) exceeds 10 KB hardware cap!"
        }

        val gray = rgbaToGrayscale(argbPixels, width, height)
        val packed = convertTo1Bit(gray, width, height, dither, invert, threshold)
        val imgId = computeImageId(width, height, packed)

        return ProcessedImage(
            id = imgId,
            width = width,
            height = height,
            rowBytes = rowBytes,
            byteData = packed
        )
    }

    /**
     * Unpacks 1-bit packed byte data into an ARGB IntArray suitable for displaying
     * in an Android Bitmap.
     * Lit pixel = White (0xFFFFFFFF), Unlit pixel = Black (0xFF000000) representing OLED.
     */
    fun unpackToArgb(
        packed: ByteArray,
        width: Int,
        height: Int
    ): IntArray {
        val rowBytes = (width + 7) / 8
        val argb = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val byteIndex = y * rowBytes + (x / 8)
                val bitOffset = 7 - (x % 8)
                val isLit = if (byteIndex < packed.size) {
                    ((packed[byteIndex].toInt() ushr bitOffset) and 1) == 1
                } else false

                // Lit pixel = pure white (micro-OLED on), Unlit = pure black (micro-OLED off)
                argb[y * width + x] = if (isLit) -0x1 else -0x1000000
            }
        }
        return argb
    }
}
