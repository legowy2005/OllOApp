package com.example.ollo.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.ollo.model.StoredImageMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles persistent file storage of 1-bit image binaries on device storage.
 * As required by hardware specifications:
 * - Images are stored as raw small files in app storage (NOT as database blobs).
 * - Files are named by their unsigned 32-bit CRC32 Image ID: "<imgId>.bin".
 * - A 4-byte header or companion meta file stores width (u16 LE) and height (u16 LE).
 */
class ImageStorage(private val context: Context) {

    private val imagesDir: File
        get() {
            val dir = File(context.filesDir, "ollo_images")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getImageFile(imgId: Long): File {
        return File(imagesDir, "$imgId.bin")
    }

    private fun getMetaFile(imgId: Long): File {
        return File(imagesDir, "$imgId.meta")
    }

    /**
     * Saves the processed 1-bit image and metadata.
     */
    suspend fun saveImage(processed: ImageProcessing.ProcessedImage): Long = withContext(Dispatchers.IO) {
        val binFile = getImageFile(processed.id)
        val metaFile = getMetaFile(processed.id)

        // Write raw 1-bit pixel data
        FileOutputStream(binFile).use { out ->
            out.write(processed.byteData)
        }

        // Write metadata (width u16 LE, height u16 LE)
        val metaBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        metaBuffer.putShort(processed.width.toShort())
        metaBuffer.putShort(processed.height.toShort())
        metaFile.writeBytes(metaBuffer.array())

        processed.id
    }

    /**
     * Reads the metadata for an image ID.
     */
    suspend fun getMetadata(imgId: Long): StoredImageMeta? = withContext(Dispatchers.IO) {
        val binFile = getImageFile(imgId)
        val metaFile = getMetaFile(imgId)
        if (!binFile.exists() || !metaFile.exists()) return@withContext null

        val metaBytes = metaFile.readBytes()
        if (metaBytes.size < 4) return@withContext null

        val buffer = ByteBuffer.wrap(metaBytes).order(ByteOrder.LITTLE_ENDIAN)
        val width = buffer.short.toInt() and 0xFFFF
        val height = buffer.short.toInt() and 0xFFFF
        val byteSize = binFile.length().toInt()

        StoredImageMeta(
            id = imgId,
            width = width,
            height = height,
            byteSize = byteSize
        )
    }

    /**
     * Loads raw 1-bit bytes from disk.
     */
    suspend fun loadRawBytes(imgId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val file = getImageFile(imgId)
        if (!file.exists()) return@withContext null
        file.readBytes()
    }

    /**
     * Loads the 1-bit image from disk as an Android displayable Bitmap.
     */
    suspend fun loadAsBitmap(imgId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val meta = getMetadata(imgId) ?: return@withContext null
        val raw = loadRawBytes(imgId) ?: return@withContext null

        val argb = ImageProcessing.unpackToArgb(raw, meta.width, meta.height)
        val bitmap = Bitmap.createBitmap(meta.width, meta.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(argb, 0, meta.width, 0, 0, meta.width, meta.height)
        bitmap
    }

    /**
     * Deletes an image from storage.
     */
    suspend fun deleteImage(imgId: Long) = withContext(Dispatchers.IO) {
        getImageFile(imgId).delete()
        getMetaFile(imgId).delete()
    }

    /**
     * Deletes all unreferenced images.
     */
    suspend fun pruneUnusedImages(referencedIds: Set<Long>) = withContext(Dispatchers.IO) {
        val files = imagesDir.listFiles() ?: return@withContext
        for (f in files) {
            val name = f.nameWithoutExtension
            val id = name.toLongOrNull() ?: continue
            if (id !in referencedIds) {
                f.delete()
            }
        }
    }

    /**
     * Decode an image from Uri or stream, downscaling to fit inside maxW x maxH.
     * Flattens transparency onto white.
     */
    suspend fun decodeAndScaleUri(
        uri: Uri,
        maxW: Int = 256,
        maxH: Int = 192
    ): Pair<IntArray, Pair<Int, Int>>? = withContext(Dispatchers.IO) {
        try {
            // First decode bounds
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val origW = options.outWidth
            val origH = options.outHeight
            if (origW <= 0 || origH <= 0) return@withContext null

            val (targetW, targetH) = ImageProcessing.computeFitDimensions(origW, origH, maxW, maxH)

            // Calculate inSampleSize
            var inSampleSize = 1
            if (origH > targetH || origW > targetW) {
                val halfHeight: Int = origH / 2
                val halfWidth: Int = origW / 2
                while ((halfHeight / inSampleSize) >= targetH && (halfWidth / inSampleSize) >= targetW) {
                    inSampleSize *= 2
                }
            }

            // Decode actual bitmap
            inputStream = context.contentResolver.openInputStream(uri)
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()
            if (sampledBitmap == null) return@withContext null

            // Scale precisely to targetW x targetH
            val scaledBitmap = if (sampledBitmap.width != targetW || sampledBitmap.height != targetH) {
                Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true)
            } else {
                sampledBitmap
            }

            val pixels = IntArray(targetW * targetH)
            scaledBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

            if (scaledBitmap != sampledBitmap) {
                scaledBitmap.recycle()
            }
            sampledBitmap.recycle()

            Pair(pixels, Pair(targetW, targetH))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
