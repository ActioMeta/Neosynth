package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [Index(value = ["artistId"]), Index(value = ["serverId"])]
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val serverId: Long,
    val title: String,
    val artistName: String,
    val artistId: String,
    val year: Int?,
    val genre: String?,
    val coverArt: String?
)