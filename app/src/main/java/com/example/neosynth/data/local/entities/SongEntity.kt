package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["artistID"]),
        Index(value = ["albumID"]),
        Index(value = ["playlistID"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: String, // from navidrome
    val title: String,
    val serverID: Long,
    val artistID: String,
    val artist: String,
    val albumID: String,
    val album: String,
    val duration: Long,
    val imageUrl: String?,
    val path: String,          // URL or path
    val isDownloaded: Boolean = false,
    val playlistID: String? = null
)