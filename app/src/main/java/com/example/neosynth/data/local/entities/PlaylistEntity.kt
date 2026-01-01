package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["serverId"])]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val serverId: Long,
    val name: String,
    val songCount: Int,
    val coverArt: String? = null
)