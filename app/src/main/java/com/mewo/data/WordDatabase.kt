package com.mewo.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mewo.domain.WordPriority
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
    @ColumnInfo(defaultValue = "100") val priority: Int = WordPriority.COMMON,
    val feminineHebrew: String? = null,
    val feminineTransliteration: String? = null,
    val feminineModification: String? = null,
    val firstShownAt: Long? = null,
) {
    fun progress() = WordProgress(id, position, shownCount, introduced, nextDueAt, priority, firstShownAt)

    fun feminineDescription(): String? = feminineHebrew?.let { "Feminine: \u200f$it\u200e ($feminineModification)" }

    fun withProgress(progress: WordProgress) =
        copy(
            shownCount = progress.shownCount,
            introduced = progress.introduced,
            nextDueAt = progress.nextDueAt,
            firstShownAt = progress.firstShownAt,
        )
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY priority, position")
    fun observe(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words ORDER BY priority, position")
    suspend fun getAll(): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(words: List<WordEntity>)

    @Update
    suspend fun update(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE id IN (:ids)")
    suspend fun deleteMerged(ids: List<String>)
}

@Database(
    entities = [WordEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = DailyLearningMigration::class),
    ],
)
abstract class WordDatabase : RoomDatabase() {
    abstract fun words(): WordDao
}

/** Older versions did not record introduction dates. Keep review words and start learners on their next post. */
class DailyLearningMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE words SET firstShownAt = 0 WHERE introduced = 1 AND shownCount >= 12")
    }
}
