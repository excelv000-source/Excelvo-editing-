package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatioId: String,
    val frameRateFps: Int,
    val durationUs: Long,
    val clipCount: Int,
    val thumbnailPath: String?,
    val documentPath: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "saved_presets")
data class SavedPresetEntity(
    @PrimaryKey val presetId: String,
    val savedAtMs: Long,
)

@Entity(tableName = "library_items")
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val itemType: String,
    val name: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAtMs: Long,
)

@Entity(tableName = "recent_media")
data class RecentMediaEntity(
    @PrimaryKey val sourceUri: String,
    val displayName: String,
    val mediaType: String,
    val mimeType: String,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val sampleRateHz: Int,
    val channelCount: Int,
    val fileSizeBytes: Long,
    val thumbnailPath: String?,
    val cachedLocalPath: String?,
    val lastImportedAtMs: Long,
)

@Entity(tableName = "media_folders")
data class MediaFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAtMs: Long,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET name = :newName, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun rename(id: String, newName: String, updatedAtMs: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SavedPresetDao {
    @Query("SELECT presetId FROM saved_presets ORDER BY savedAtMs DESC")
    fun observeSavedPresetIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_presets WHERE presetId = :presetId)")
    suspend fun isSaved(presetId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SavedPresetEntity)

    @Query("DELETE FROM saved_presets WHERE presetId = :presetId")
    suspend fun unsave(presetId: String)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<LibraryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: LibraryItemEntity)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface RecentMediaDao {
    @Query("SELECT * FROM recent_media ORDER BY lastImportedAtMs DESC LIMIT 100")
    fun observeRecentMedia(): Flow<List<RecentMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(item: RecentMediaEntity)

    @Query("DELETE FROM recent_media WHERE sourceUri = :sourceUri")
    suspend fun deleteRecent(sourceUri: String)

    @Query("SELECT * FROM media_folders ORDER BY addedAtMs DESC")
    fun observeFolders(): Flow<List<MediaFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: MediaFolderEntity)

    @Query("DELETE FROM media_folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)
}

@Database(
    entities = [
        ProjectEntity::class,
        SavedPresetEntity::class,
        LibraryItemEntity::class,
        RecentMediaEntity::class,
        MediaFolderEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ExcevoDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun savedPresetDao(): SavedPresetDao
    abstract fun libraryDao(): LibraryDao
    abstract fun recentMediaDao(): RecentMediaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_presets` (`presetId` TEXT NOT NULL, `savedAtMs` INTEGER NOT NULL, PRIMARY KEY(`presetId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_items` (`id` TEXT NOT NULL, `itemType` TEXT NOT NULL, `name` TEXT NOT NULL, `localPath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_media` (`sourceUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `durationUs` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `fps` REAL NOT NULL, `sampleRateHz` INTEGER NOT NULL, `channelCount` INTEGER NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `thumbnailPath` TEXT, `cachedLocalPath` TEXT, `lastImportedAtMs` INTEGER NOT NULL, PRIMARY KEY(`sourceUri`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_folders` (`treeUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`treeUri`))"
                )
            }
        }

        @Volatile
        private var instance: ExcevoDatabase? = null

        fun getInstance(context: Context): ExcevoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExcevoDatabase::class.java,
                    "excevo_edit.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
