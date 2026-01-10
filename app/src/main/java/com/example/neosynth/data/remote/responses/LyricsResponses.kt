package com.example.neosynth.data.remote.responses

import com.google.gson.annotations.SerializedName

// LRCLIB Response
data class LrclibResponse(
    val id: Int?,
    @SerializedName("trackName") val trackName: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("albumName") val albumName: String?,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("instrumental") val instrumental: Boolean?,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?
)

// Musixmatch Response
data class MusixmatchResponse(
    val message: MusixmatchMessage
)

data class MusixmatchMessage(
    val header: MusixmatchHeader,
    val body: MusixmatchBody
)

data class MusixmatchHeader(
    @SerializedName("status_code") val statusCode: Int,
    @SerializedName("execute_time") val executeTime: Double?
)

data class MusixmatchBody(
    val lyrics: MusixmatchLyrics?
)

data class MusixmatchLyrics(
    @SerializedName("lyrics_id") val lyricsId: Long?,
    @SerializedName("lyrics_body") val lyricsBody: String?,
    @SerializedName("script_tracking_url") val scriptTrackingUrl: String?,
    @SerializedName("pixel_tracking_url") val pixelTrackingUrl: String?,
    @SerializedName("lyrics_copyright") val lyricsCopyright: String?
)

// Netease Cloud Music Response
data class NeteaseSearchResponse(
    val result: NeteaseSearchResult?,
    val code: Int
)

data class NeteaseSearchResult(
    val songs: List<NeteaseSong>?,
    val songCount: Int?
)

data class NeteaseSong(
    val id: Long,
    val name: String?,
    val artists: List<NeteaseArtist>?,
    val album: NeteaseAlbum?,
    val duration: Int?
)

data class NeteaseArtist(
    val id: Long,
    val name: String?
)

data class NeteaseAlbum(
    val id: Long,
    val name: String?
)

data class NeteaseLyricResponse(
    val lrc: NeteaseLrc?,
    val klyric: NeteaseLrc?, // Karaoke lyrics (word by word sync)
    val tlyric: NeteaseLrc?, // Translated lyrics
    val code: Int
)

data class NeteaseLrc(
    val version: Int?,
    val lyric: String? // LRC format string
)
