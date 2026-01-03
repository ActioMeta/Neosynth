package com.example.neosynth.data.mappers

import com.example.neosynth.data.local.entities.SongEntity
import com.example.neosynth.data.local.entities.AlbumEntity
import com.example.neosynth.data.local.entities.ArtistEntity
import com.example.neosynth.domain.model.Song
import com.example.neosynth.domain.model.Album
import com.example.neosynth.domain.model.Artist
import com.example.neosynth.domain.model.MusicSourceType
import org.json.JSONObject

/**
 * Mappers para convertir entre Domain Models y Room Entities
 */

// ===== SONG MAPPERS =====

fun Song.toEntity(isDownloaded: Boolean = false, playlistId: String? = null): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        serverID = 0L, // DEPRECATED
        sourceType = sourceType.name,
        sourceId = sourceId,
        artistID = artistId,
        artist = artist,
        albumID = albumId,
        album = album,
        duration = duration,
        imageUrl = coverArtUrl,
        path = "", // Will be set when downloading
        isDownloaded = isDownloaded,
        playlistID = playlistId,
        year = year,
        genre = genre,
        trackNumber = trackNumber,
        metadata = metadata.toJson()
    )
}

fun SongEntity.toDomain(): Song {
    return Song(
        id = id,
        title = title,
        artist = artist,
        artistId = artistID,
        album = album,
        albumId = albumID,
        duration = duration,
        coverArtUrl = imageUrl,
        sourceType = MusicSourceType.valueOf(sourceType),
        sourceId = sourceId,
        year = year,
        genre = genre,
        trackNumber = trackNumber,
        metadata = metadata?.fromJson() ?: emptyMap()
    )
}

// ===== ALBUM MAPPERS =====

fun Album.toEntity(): AlbumEntity {
    return AlbumEntity(
        id = id,
        serverId = 0L, // DEPRECATED
        sourceType = sourceType.name,
        sourceId = sourceId,
        title = name,
        artistName = artistName,
        artistId = artistId,
        year = year,
        genre = genre,
        coverArt = coverArtUrl,
        songCount = songCount,
        duration = duration,
        metadata = metadata.toJson()
    )
}

fun AlbumEntity.toDomain(): Album {
    return Album(
        id = id,
        name = title,
        artistId = artistId,
        artistName = artistName,
        coverArtUrl = coverArt,
        sourceType = MusicSourceType.valueOf(sourceType),
        sourceId = sourceId,
        year = year,
        songCount = songCount ?: 0,
        duration = duration ?: 0L,
        genre = genre,
        metadata = metadata?.fromJson() ?: emptyMap()
    )
}

// ===== ARTIST MAPPERS =====

fun Artist.toEntity(): ArtistEntity {
    return ArtistEntity(
        id = id,
        serverId = 0L, // DEPRECATED
        sourceType = sourceType.name,
        sourceId = sourceId,
        name = name,
        imageUrl = coverArtUrl,
        albumCount = albumCount,
        metadata = metadata.toJson()
    )
}

fun ArtistEntity.toDomain(): Artist {
    return Artist(
        id = id,
        name = name,
        coverArtUrl = imageUrl,
        sourceType = MusicSourceType.valueOf(sourceType),
        sourceId = sourceId,
        albumCount = albumCount ?: 0,
        metadata = metadata?.fromJson() ?: emptyMap()
    )
}

// ===== JSON HELPERS =====

private fun Map<String, String>.toJson(): String {
    if (isEmpty()) return "{}"
    val json = JSONObject()
    forEach { (key, value) ->
        json.put(key, value)
    }
    return json.toString()
}

private fun String.fromJson(): Map<String, String> {
    try {
        val json = JSONObject(this)
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            map[key] = json.getString(key)
        }
        return map
    } catch (e: Exception) {
        return emptyMap()
    }
}
