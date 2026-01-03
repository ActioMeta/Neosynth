package com.example.neosynth

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NeoSynthApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        Log.d("NeoSynthApp", "Application initialized")
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("NeoSynthApp", "WorkManager configuration requested, workerFactory initialized: ${::workerFactory.isInitialized}")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                // Optimización para playlists grandes (1000+ canciones)
                .setMaxSchedulerLimit(100) // Permitir hasta 100 workers encolados simultáneamente
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
        }

    // Configuración de Coil para optimizar carga de imágenes
    override fun newImageLoader(): ImageLoader {
        val isDebug = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Usar hasta 25% de RAM para caché de imágenes
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.03) // Usar hasta 3% de almacenamiento para caché
                    .build()
            }
            .respectCacheHeaders(false) // Ignorar cache headers del servidor
            .crossfade(true) // Animación suave al cargar imágenes
            .apply {
                if (isDebug) {
                    logger(DebugLogger())
                }
            }
            .build()
    }
}