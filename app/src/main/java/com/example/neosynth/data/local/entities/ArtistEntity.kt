package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["sourceType", "sourceId"])
    ]
)
data class ArtistEntity(
    @PrimaryKey val id: String, // Composite: "SUBSONIC_serverId_artistId"
    val serverId: Long, // DEPRECATED: Keep for migration
    val sourceType: String, // "SUBSONIC", "LOCAL_FILES", etc
    val sourceId: String, // Server ID or source identifier
    val name: String,
    val imageUrl: String? = null,
    val albumCount: Int? = null,
    val metadata: String? = null // JSON for source-specific data
)