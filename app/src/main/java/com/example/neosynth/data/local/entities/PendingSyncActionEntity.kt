package com.example.neosynth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync_actions")
data class PendingSyncActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverId: Long,
    val actionType: String, // "CREATE_PLAYLIST", "ADD_SONG", "REMOVE_SONG", "DELETE_PLAYLIST"
    val payload: String,    // JSON string depending on actionType
    val createdAt: Long = System.currentTimeMillis(),
    val isProcessing: Boolean = false // Prevent duplicate processing
)
