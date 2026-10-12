package com.mewo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.mewo.domain.WordProgress
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val hebrew: String,
    val english: String,
    val transliteration: String,
    val shownCount: Int = 0,
    val introduced: Boolean = false,
    val nextDueAt: Long = 0,
) {
    fun progress() = WordProgress(id, position, shownCount, introduced, nextDueAt)

    fun withProgress(progress: WordProgress) =
        copy(
            shownCount = progress.shownCount,
            introduced = progress.introduced,
            nextDueAt = progress.nextDueAt,
        )
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY position")
    fun observe(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words ORDER BY position")
    suspend fun getAll(): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(words: List<WordEntity>)

    @Update
    suspend fun update(words: List<WordEntity>)
}

@Database(entities = [WordEntity::class], version = 1, exportSchema = true)
abstract class WordDatabase : RoomDatabase() {
    abstract fun words(): WordDao
}
