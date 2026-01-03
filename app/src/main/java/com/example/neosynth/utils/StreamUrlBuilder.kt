package com.example.neosynth.utils

import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.preferences.DownloadQuality
import com.example.neosynth.data.preferences.StreamQuality

object StreamUrlBuilder {
    
    /**
     * Construye la URL de streaming con parámetros de transcodificación
     */
    fun buildStreamUrl(
        server: ServerEntity,
        songId: String,
        quality: StreamQuality
    ): String {
        val baseUrl = server.url.removeSuffix("/")
        val params = mutableListOf(
            "id=$songId",
            "u=${server.username}",
            "t=${server.token}",
            "s=${server.salt}",
            "v=1.16.1",
            "c=NeoSynth"
        )
        
        // Agregar parámetros de transcodificación si no es LOSSLESS
        if (quality != StreamQuality.LOSSLESS) {
            params.add("maxBitRate=${quality.bitrate}")
            params.add("format=${quality.format}")
        }
        
        return "$baseUrl/rest/stream?${params.joinToString("&")}"
    }
    
    /**
     * Construye la URL de descarga con parámetros de transcodificación
     */
    fun buildDownloadUrl(
        server: ServerEntity,
        songId: String,
        quality: DownloadQuality
    ): String {
        val baseUrl = server.url.removeSuffix("/")
        val params = mutableListOf(
            "id=$songId",
            "u=${server.username}",
            "t=${server.token}",
            "s=${server.salt}",
            "v=1.16.1",
            "c=NeoSynth"
        )
        
        // Agregar parámetros de transcodificación si no es LOSSLESS
        if (quality != DownloadQuality.LOSSLESS) {
            params.add("maxBitRate=${quality.bitrate}")
            params.add("format=${quality.format}")
        }
        
        return "$baseUrl/rest/download?${params.joinToString("&")}"
    }
}
