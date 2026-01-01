package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [Index(value = ["serverId"])]
)
data class ArtistEntity(
    @PrimaryKey val id: String,
    val serverId: Long,
    val name: String,
    val imageUrl: String? = null
)