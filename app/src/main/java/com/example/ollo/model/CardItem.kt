package com.example.ollo.model

/**
 * Domain model representing a flashcard for the Ollo AR glasses.
 *
 * @param id Unique database identifier.
 * @param frontText Text shown on the front side (max 100 ASCII chars, \n for linebreaks).
 * @param backText Text shown on the back side (max 100 ASCII chars, \n for linebreaks).
 * @param frontImgId CRC32 image ID (0 if no image).
 * @param backImgId CRC32 image ID (0 if no image).
 * @param createdAt Creation epoch milliseconds.
 * @param updatedAt Last update epoch milliseconds.
 */
data class CardItem(
    val id: Long = 0,
    val frontText: String = "",
    val backText: String = "",
    val frontImgId: Long = 0L,
    val backImgId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val hasFrontImage: Boolean get() = frontImgId != 0L
    val hasBackImage: Boolean get() = backImgId != 0L
}

data class StoredImageMeta(
    val id: Long,
    val width: Int,
    val height: Int,
    val byteSize: Int
)

data class OlloSettings(
    val defaultDither: Boolean = true,
    val defaultInvert: Boolean = false,
    val defaultThreshold: Int = 128,
    val maxImageWidth: Int = 256,
    val maxImageHeight: Int = 192,
    val storageBudgetKb: Int = 1024, // 1 MB default
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = "",
    val virtualPeripheralEnabled: Boolean = false
)
