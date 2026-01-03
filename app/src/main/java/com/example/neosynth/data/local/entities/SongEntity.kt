package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["artistID"]),
        Index(value = ["albumID"]),
        Index(value = ["playlistID"]),
        Index(value = ["sourceType", "sourceId"]) // For multi-source queries
    ]
)
data class SongEntity(
    @PrimaryKey val id: String, // Composite: "SUBSONIC_serverId_songId" or "LOCAL_FILES_uuid"
    val title: String,
    val serverID: Long, // DEPRECATED: Keep for migration compatibility
    val sourceType: String, // "SUBSONIC", "LOCAL_FILES", etc
    val sourceId: String, // Server ID or source identifier
    val artistID: String,
    val artist: String,
    val albumID: String,
    val album: String,
    val duration: Long,
    val imageUrl: String?,
    val path: String,          // URL or local file path
    val isDownloaded: Boolean = false,
    val isFavorite: Boolean = false,
    val playlistID: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val metadata: String? = null // JSON for source-specific data
)