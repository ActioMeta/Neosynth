package com.example.neosynth.data.worker

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Singleton para rastrear progreso de descargas de playlists de forma thread-safe
 * Resuelve el problema de race conditions en notificaciones consolidadas
 */
object DownloadProgress {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()
    
    /**
     * Incrementa el contador para una playlist y devuelve el nuevo valor
     * Thread-safe: múltiples workers pueden llamar simultáneamente
     */
    @Deprecated("Use MusicRepository.getPlaylistDownloadedCount instead for robust tracking")
    fun increment(playlistId: String): Int {
        return counters.getOrPut(playlistId) { AtomicInteger(0) }
            .incrementAndGet()
    }
    
    /**
     * Obtiene el progreso actual de una playlist sin incrementar
     */
    @Deprecated("Use MusicRepository.getPlaylistDownloadedCount instead")
    fun getCurrent(playlistId: String): Int {
        return counters[playlistId]?.get() ?: 0
    }
    
    /**
     * Resetea el contador de una playlist (llamar al finalizar todas las descargas)
     */
    @Deprecated("No longer needed with DB-based tracking")
    fun reset(playlistId: String) {
        counters.remove(playlistId)
    }
    
    /**
     * Limpia todos los contadores
     */
    @Deprecated("No longer needed")
    fun clear() {
        counters.clear()
    }
}
