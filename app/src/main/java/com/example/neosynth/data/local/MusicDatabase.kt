package com.example.neosynth.data.local

import androidx.compose.ui.Modifier
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.neosynth.data.local.entities.AlbumEntity
import com.example.neosynth.data.local.entities.ArtistEntity
import com.example.neosynth.data.local.entities.PlaylistEntity
import com.example.neosynth.data.local.entities.PlaylistSongCrossRef
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.local.entities.SongEntity

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ServerEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract val musicDao : MusicDao
    abstract val serverDao : ServerDao
}