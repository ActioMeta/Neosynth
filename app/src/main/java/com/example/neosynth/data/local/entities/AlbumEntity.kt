package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["artistId"]), 
        Index(value = ["serverId"]),
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class AlbumEntity(
    @PrimaryKey val id: String, // Composite: "SUBSONIC_serverId_albumId"
    val serverId: Long, // DEPRECATED: Keep for migration
    val sourceType: String, // "SUBSONIC", "LOCAL_FILES", etc
    val sourceId: String, // Server ID or source identifier
    val title: String,
    val artistName: String,
    val artistId: String,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val songCount: Int? = null,
    val duration: Long? = null,
    val metadata: String? = null // JSON for source-specific data
)