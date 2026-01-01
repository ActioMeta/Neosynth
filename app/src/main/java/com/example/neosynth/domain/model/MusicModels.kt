package com.example.neosynth.domain.model

data class Song(
    val id: String,
    val serverId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val imageUrl: String?,
    val mediaUri: String,
    val isOffline: Boolean = false
)

data class Playlist(
    val id: String,
    val serverId: Long,
    val name: String,
    val songCount: Int
)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int? = null
)

data class Album(
    val id: String,
    val serverId: Long,
    val title: String,
    val artist: String,
    val year: Int?,
    val genre: String?,
    val coverArtUrl: String?,
    val songCount: Int? = null
)

data class Genre(
    val name: String,
    val songCount: Int? = null,
    val albumCount: Int? = null
)