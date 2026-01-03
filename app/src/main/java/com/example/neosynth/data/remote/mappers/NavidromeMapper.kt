package com.example.neosynth.data.remote.mappers

import com.example.neosynth.data.local.entities.*
import com.example.neosynth.data.remote.responses.*

fun SubsonicResponse.toSongEntities(serverId: Long, pId: String? = null): List<SongEntity> {
    val dtos = this.response.searchResult3?.song
        ?: this.response.albumDetails?.song
        ?: this.response.playlistDetails?.entry
        ?: emptyList()

    return dtos.map { dto ->
        SongEntity(
            id = dto.id,
            serverID = serverId,
            sourceType = "SUBSONIC",
            sourceId = serverId.toString(),
            artistID = dto.artistId ?: "",
            albumID = dto.albumId ?: "",
            title = dto.title,
            artist = dto.artist,
            album = dto.album,
            duration = dto.duration.toLong(),
            imageUrl = dto.coverArt,
            path = dto.path ?: "",
            isDownloaded = false,
            playlistID = pId ?: ""
        )
    }
}

fun SubsonicResponse.toArtistEntities(serverId: Long): List<ArtistEntity> {
    return this.response.artistsContainer?.indices?.flatMap { index ->
        index.artist?.map { dto ->
            ArtistEntity(
                id = dto.id,
                serverId = serverId,
                sourceType = "SUBSONIC",
                sourceId = serverId.toString(),
                name = dto.name,
                imageUrl = dto.artistImageUrl
            )
        } ?: emptyList()
    } ?: emptyList()
}

fun SubsonicResponse.toAlbumEntities(serverId: Long): List<AlbumEntity> {
    return this.response.albumList?.album?.map { dto ->
        AlbumEntity(
            id = dto.id,
            serverId = serverId,
            sourceType = "SUBSONIC",
            sourceId = serverId.toString(),
            artistId = dto.artistId ?: "",
            title = dto.title,
            artistName = dto.artist,
            year = dto.year,
            genre = dto.genre,
            coverArt = dto.coverArt
        )
    } ?: emptyList()
}

fun SubsonicResponse.toPlaylistEntities(serverId: Long): List<PlaylistEntity> {
    return this.response.playlistsContainer?.playlist?.map { dto ->
        PlaylistEntity(
            id = dto.id,
            serverId = serverId,
            name = dto.name,
            songCount = dto.songCount,
            coverArt = dto.coverArt
        )
    } ?: emptyList()
}