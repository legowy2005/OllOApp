package com.example.ollo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY id ASC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY id ASC")
    suspend fun getAllCardsSync(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE front_text LIKE '%' || :query || '%' OR back_text LIKE '%' || :query || '%' ORDER BY id ASC")
    fun searchCards(query: String): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards")
    fun getCardCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: Long)

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()

    @Query("SELECT front_img_id FROM cards WHERE front_img_id != 0 UNION SELECT back_img_id FROM cards WHERE back_img_id != 0")
    suspend fun getAllReferencedImageIds(): List<Long>
}
