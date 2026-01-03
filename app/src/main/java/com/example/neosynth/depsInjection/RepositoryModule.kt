package com.example.neosynth.depsInjection

import com.example.neosynth.data.local.MusicDao
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.repository.MusicRepositoryImpl
import com.example.neosynth.data.sources.SubsonicDataSource
import com.example.neosynth.domain.repository.IMusicRepository
import com.example.neosynth.domain.repository.MusicDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Módulo Hilt para proveer el repositorio y las fuentes de música
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideMusicRepository(
        musicDao: MusicDao,
        serverDao: ServerDao,
        api: NavidromeApiService
    ): IMusicRepository {
        val repository = MusicRepositoryImpl(musicDao)
        
        // Registrar fuentes disponibles al inicio
        runBlocking {
            // Obtener servidores Subsonic/Navidrome configurados
            val servers = serverDao.getAllServers()
            servers.forEach { server ->
                val source = SubsonicDataSource(api, server)
                repository.registerSource(source)
            }
        }
        
        return repository
    }
    
    // Proveer también como MusicRepositoryImpl para código que aún lo necesite
    @Provides
    @Singleton
    fun provideMusicRepositoryImpl(repository: IMusicRepository): MusicRepositoryImpl {
        return repository as MusicRepositoryImpl
    }
}
