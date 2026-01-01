package com.example.neosynth.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.neosynth.data.local.entities.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers")
    fun observeAllServers(): Flow<List<ServerEntity>>
    
    @Query("SELECT * FROM servers")
    suspend fun getAllServers(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveServer(): ServerEntity?
    
    @Query("SELECT * FROM servers WHERE isActive = 1 LIMIT 1")
    fun getActiveServerFlow(): Flow<ServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Query("UPDATE servers SET isActive = (id = :serverId)")
    suspend fun setActiveServer(serverId: Long)

    @Query("UPDATE servers SET isActive = :isActive WHERE id = :serverId")
    suspend fun updateActiveStatus(serverId: Long, isActive: Boolean)
    
    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun deleteServerById(serverId: Long)
}

public fun buildCoverArtUrl(server: com.example.neosynth.data.local.entities.ServerEntity, coverArtId: String?): String? {
    if (coverArtId == null) return null
    val url = server.url.trim().removeSuffix("/")
    return "${url}/rest/getCoverArt.view?"+
            "id=$coverArtId" +
            "&u=${server.username}" +
            "&t=${server.token}" +
            "&s=${server.salt}" +
            "&v=1.16.1" +
            "&c=NeoSynth"
}

