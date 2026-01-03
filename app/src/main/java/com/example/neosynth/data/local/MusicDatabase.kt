package com.example.neosynth.data.local

import androidx.compose.ui.Modifier
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.neosynth.data.local.entities.AlbumEntity
import com.example.neosynth.data.local.entities.ArtistEntity
import com.example.neosynth.data.local.entities.PlaylistEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.local.entities.SongEntity

/**
 * Migration from version 2 to 3: Add multi-source support
 * Adds sourceType, sourceId, and metadata columns to songs, albums, and artists
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to songs table
        database.execSQL("ALTER TABLE songs ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SUBSONIC'")
        database.execSQL("ALTER TABLE songs ADD COLUMN sourceId TEXT NOT NULL DEFAULT '0'")
        database.execSQL("ALTER TABLE songs ADD COLUMN year INTEGER")
        database.execSQL("ALTER TABLE songs ADD COLUMN genre TEXT")
        database.execSQL("ALTER TABLE songs ADD COLUMN trackNumber INTEGER")
        database.execSQL("ALTER TABLE songs ADD COLUMN metadata TEXT")
        
        // Update sourceId to match serverID for existing records
        database.execSQL("UPDATE songs SET sourceId = CAST(serverID AS TEXT)")
        
        // Create index for multi-source queries
        database.execSQL("CREATE INDEX IF NOT EXISTS index_songs_sourceType_sourceId ON songs(sourceType, sourceId)")
        
        // Add new columns to albums table
        database.execSQL("ALTER TABLE albums ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SUBSONIC'")
        database.execSQL("ALTER TABLE albums ADD COLUMN sourceId TEXT NOT NULL DEFAULT '0'")
        database.execSQL("ALTER TABLE albums ADD COLUMN songCount INTEGER")
        database.execSQL("ALTER TABLE albums ADD COLUMN duration INTEGER")
        database.execSQL("ALTER TABLE albums ADD COLUMN metadata TEXT")
        
        // Update sourceId to match serverId for existing records
        database.execSQL("UPDATE albums SET sourceId = CAST(serverId AS TEXT)")
        
        // Create index for multi-source queries
        database.execSQL("CREATE INDEX IF NOT EXISTS index_albums_sourceType_sourceId ON albums(sourceType, sourceId)")
        
        // Add new columns to artists table
        database.execSQL("ALTER TABLE artists ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'SUBSONIC'")
        database.execSQL("ALTER TABLE artists ADD COLUMN sourceId TEXT NOT NULL DEFAULT '0'")
        database.execSQL("ALTER TABLE artists ADD COLUMN albumCount INTEGER")
        database.execSQL("ALTER TABLE artists ADD COLUMN metadata TEXT")
        
        // Update sourceId to match serverId for existing records
        database.execSQL("UPDATE artists SET sourceId = CAST(serverId AS TEXT)")
        
        // Create index for multi-source queries
        database.execSQL("CREATE INDEX IF NOT EXISTS index_artists_sourceType_sourceId ON artists(sourceType, sourceId)")
    }
}

/**
 * Migration from version 3 to 4: Add favorites support
 * Adds isFavorite column to songs table
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add isFavorite column to songs table
        database.execSQL("ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ServerEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class],
    version = 4, // Incremented from 3 to 4
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract val musicDao : MusicDao
    abstract val serverDao : ServerDao
}