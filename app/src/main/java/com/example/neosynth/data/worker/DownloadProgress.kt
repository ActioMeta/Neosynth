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
    fun increment(playlistId: String): Int {
        return counters.getOrPut(playlistId) { AtomicInteger(0) }
            .incrementAndGet()
    }
    
    /**
     * Obtiene el progreso actual de una playlist sin incrementar
     */
    fun getCurrent(playlistId: String): Int {
        return counters[playlistId]?.get() ?: 0
    }
    
    /**
     * Resetea el contador de una playlist (llamar al finalizar todas las descargas)
     */
    fun reset(playlistId: String) {
        counters.remove(playlistId)
    }
    
    /**
     * Limpia todos los contadores
     */
    fun clear() {
        counters.clear()
    }
}
