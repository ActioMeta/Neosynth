package com.example.neosynth.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.preferences.*
import com.example.neosynth.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val musicRepository: MusicRepository,
    private val settingsPreferences: SettingsPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _serverInfo = MutableStateFlow<ServerEntity?>(null)
    val serverInfo: StateFlow<ServerEntity?> = _serverInfo
    
    private val _allServers = MutableStateFlow<List<ServerEntity>>(emptyList())
    val allServers: StateFlow<List<ServerEntity>> = _allServers

    private val _cacheSize = MutableStateFlow("Calculando...")
    val cacheSize: StateFlow<String> = _cacheSize

    private val _downloadedCount = MutableStateFlow(0)
    val downloadedCount: StateFlow<Int> = _downloadedCount
    
    // Settings flows
    val audioSettings: StateFlow<AudioSettings> = settingsPreferences.audioSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioSettings())
    
    val appSettings: StateFlow<AppSettings> = settingsPreferences.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun loadSettings() {
        viewModelScope.launch {
            // Load server info
            _serverInfo.value = serverDao.getActiveServer()
            _allServers.value = serverDao.getAllServers()

            // Load downloaded count
            val downloaded = musicRepository.getDownloadedSongs().first()
            _downloadedCount.value = downloaded.size

            // Calculate cache size
            calculateCacheSize()
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            try {
                val cacheDir = appContext.cacheDir
                val size = calculateDirectorySize(cacheDir)
                _cacheSize.value = formatFileSize(size)
            } catch (e: Exception) {
                _cacheSize.value = "Error al calcular"
            }
        }
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size: Long = 0
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "%.2f GB".format(size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val cacheDir = appContext.cacheDir
                deleteDirectory(cacheDir)
                calculateCacheSize()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun deleteAllDownloads() {
        viewModelScope.launch {
            try {
                // Eliminar todas las canciones descargadas de la base de datos
                musicRepository.deleteAllDownloadedSongs()
                
                // Eliminar archivos de música
                val musicDir = File(appContext.filesDir, "music")
                if (musicDir.exists()) {
                    deleteDirectory(musicDir)
                    musicDir.mkdirs() // Recrear el directorio vacío
                }
                
                // Eliminar archivos de cover arts
                val coversDir = File(appContext.filesDir, "covers")
                if (coversDir.exists()) {
                    deleteDirectory(coversDir)
                    coversDir.mkdirs() // Recrear el directorio vacío
                }
                
                // Actualizar el contador de descargas
                val downloaded = musicRepository.getDownloadedSongs().first()
                _downloadedCount.value = downloaded.size
                
                android.util.Log.d("SettingsViewModel", "All downloads deleted successfully")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error deleting downloads", e)
                e.printStackTrace()
            }
        }
    }

    private fun deleteDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectory(file)
            }
            file.delete()
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // Mark server as inactive
                _serverInfo.value?.let { server ->
                    serverDao.updateActiveStatus(server.id, false)
                }
                // TODO: Navigate to login screen
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Audio Settings Functions
    fun updateCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.updateCrossfadeEnabled(enabled)
        }
    }
    
    fun updateNormalizeVolume(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.updateNormalizeVolume(enabled)
        }
    }
    
    fun updateStreamQuality(quality: StreamQuality) {
        viewModelScope.launch {
            settingsPreferences.updateStreamQuality(quality)
        }
    }
    
    fun updateWifiQuality(quality: StreamQuality) {
        viewModelScope.launch {
            settingsPreferences.updateWifiQuality(quality)
        }
    }
    
    fun updateMobileQuality(quality: StreamQuality) {
        viewModelScope.launch {
            settingsPreferences.updateMobileQuality(quality)
        }
    }
    
    // App Settings Functions
    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsPreferences.updateThemeMode(mode)
        }
    }
    
    fun updateDynamicColors(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.updateDynamicColors(enabled)
        }
    }
    
    // Server Management
    fun addServer(server: ServerEntity) {
        viewModelScope.launch {
            // Deactivate all servers
            _allServers.value.forEach { 
                serverDao.updateActiveStatus(it.id, false)
            }
            // Insert new server as active
            serverDao.insertServer(server.copy(isActive = true))
            loadSettings()
        }
    }
    
    fun updateServer(server: ServerEntity) {
        viewModelScope.launch {
            serverDao.insertServer(server)
            loadSettings()
        }
    }
    
    fun deleteServer(serverId: Long) {
        viewModelScope.launch {
            serverDao.deleteServerById(serverId)
            loadSettings()
        }
    }
    
    fun setActiveServer(serverId: Long) {
        viewModelScope.launch {
            // Deactivate all
            _allServers.value.forEach {
                serverDao.updateActiveStatus(it.id, false)
            }
            // Activate selected
            serverDao.updateActiveStatus(serverId, true)
            loadSettings()
        }
    }
}
