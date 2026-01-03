package com.example.neosynth.domain.model

/**
 * Tipo de fuente de música
 */
enum class MusicSourceType {
    SUBSONIC,      // Navidrome, Airsonic, etc
    LOCAL_FILES,   // Archivos del dispositivo
    SPOTIFY,       // Futuro: Spotify
    YOUTUBE_MUSIC, // Futuro: YouTube Music
    JELLYFIN,      // Futuro: Jellyfin
    PLEX          // Futuro: Plex
}

/**
 * Modelo de dominio para una canción (agnóstico a la fuente)
 */
data class Song(
    val id: String,              // ID único (puede incluir prefijo de fuente)
    val title: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val duration: Long,          // Milisegundos
    val coverArtUrl: String?,
    val sourceType: MusicSourceType,
    val sourceId: String,        // ID del servidor/fuente específica
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val metadata: Map<String, String> = emptyMap() // Metadata adicional específica de la fuente
)

/**
 * Modelo de dominio para un artista
 */
data class Artist(
    val id: String,
    val name: String,
    val coverArtUrl: String?,
    val sourceType: MusicSourceType,
    val sourceId: String,
    val albumCount: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Modelo de dominio para un álbum
 */
data class Album(
    val id: String,
    val name: String,
    val artistId: String,
    val artistName: String,
    val coverArtUrl: String?,
    val sourceType: MusicSourceType,
    val sourceId: String,
    val year: Int? = null,
    val songCount: Int = 0,
    val duration: Long = 0,
    val genre: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Modelo de dominio para una playlist
 */
data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverArtUrl: String?,
    val sourceType: MusicSourceType,
    val sourceId: String,
    val songCount: Int = 0,
    val duration: Long = 0,
    val isPublic: Boolean = false,
    val owner: String? = null,
    val songs: List<Song> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Modelo de dominio para un género musical
 */
data class Genre(
    val name: String,
    val songCount: Int? = null,
    val albumCount: Int? = null
)
