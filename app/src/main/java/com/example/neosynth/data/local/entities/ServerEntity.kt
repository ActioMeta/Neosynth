package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val username: String,
    val token: String,
    val salt: String,
    val type: ServerType = ServerType.SUBSONIC,
    val isActive: Boolean = false
)

enum class ServerType { SUBSONIC, JELLYFIN }