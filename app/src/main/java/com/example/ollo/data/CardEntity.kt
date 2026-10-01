package com.example.ollo.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.ollo.model.CardItem

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "front_text")
    val frontText: String = "",
    @ColumnInfo(name = "back_text")
    val backText: String = "",
    @ColumnInfo(name = "front_img_id")
    val frontImgId: Long = 0L,
    @ColumnInfo(name = "back_img_id")
    val backImgId: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): CardItem = CardItem(
        id = id,
        frontText = frontText,
        backText = backText,
        frontImgId = frontImgId,
        backImgId = backImgId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(item: CardItem): CardEntity = CardEntity(
            id = item.id,
            frontText = item.frontText,
            backText = item.backText,
            frontImgId = item.frontImgId,
            backImgId = item.backImgId,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }
}
