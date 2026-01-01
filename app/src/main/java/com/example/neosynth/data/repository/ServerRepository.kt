package com.example.neosynth.data.repository

import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepository @Inject constructor(
    private val serverDao: ServerDao
) {
    val allServers = serverDao.observeAllServers()

    suspend fun getActiveServer(): ServerEntity? {
        return serverDao.getActiveServer()
    }

    suspend fun insertServer(server: ServerEntity) {
        serverDao.insertServer(server)
    }

    suspend fun setActiveServer(serverId: Long) {
        serverDao.setActiveServer(serverId)
    }
}