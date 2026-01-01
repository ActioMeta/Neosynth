package com.example.neosynth.data.remote.responses

import com.google.gson.annotations.SerializedName

data class SubsonicResponse(
    @SerializedName("subsonic-response") val response: ResponseContent
)

data class ResponseContent(
    val status: String,
    val version: String,
    @SerializedName("searchResult3") val searchResult3: SearchResult? = null,
    @SerializedName("artists") val artistsContainer: ArtistsContainer? = null,
    @SerializedName("artist") val artist: ArtistDto? = null,
    @SerializedName("artistInfo2") val artistInfo: ArtistInfo? = null,
    @SerializedName("albumList") val albumList: AlbumList? = null,
    @SerializedName("album") val albumDetails: AlbumDetails? = null,
    @SerializedName("playlists") val playlistsContainer: PlaylistsContainer? = null,
    @SerializedName("playlist") val playlistDetails: PlaylistDetails? = null,
    @SerializedName("genres") val genres: GenresContainer? = null,
    @SerializedName("songsByGenre") val songsByGenre: SongsByGenreContainer? = null,

    @SerializedName("albumList2") val albumList2: AlbumList? = null,
    @SerializedName("randomSongs") val randomSongs: SongListDto? = null
)

// Artist Info
data class ArtistInfo(
    val biography: String? = null,
    val musicBrainzId: String? = null,
    val lastFmUrl: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null
)


data class SearchResult(
    val song: List<SongDto>? = null
)

data class SongListDto(
    @SerializedName("song") val song: List<SongDto> = emptyList()
)

data class SongDto(
    val id: String,
    val title: String,
    val artist: String,
    @SerializedName("artistId") val artistId: String? = null,
    val album: String,
    @SerializedName("albumId") val albumId: String? = null,
    val duration: Int,
    val coverArt: String? = null,
    val path: String? = null,
    val year: Int? = null
)

data class ArtistsContainer(
    @SerializedName("index") val indices: List<ArtistIndex>? = null
)

data class ArtistIndex(
    val name: String,
    val artist: List<ArtistDto>? = null
)

data class ArtistDto(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
    val artistImageUrl: String? = null,
    val coverArt: String? = null,
    val album: List<AlbumDto>? = null
)


//albums
data class AlbumList(
    val album: List<AlbumDto>? = null
)

data class AlbumDto(
    val id: String,
    @SerializedName(value = "title", alternate = ["name"])
    val title: String,    val artist: String,
    val artistId: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null
)

data class AlbumDetails(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val song: List<SongDto>? = null
)

data class PlaylistsContainer(
    val playlist: List<PlaylistDto>? = null
)

data class PlaylistDto(
    val id: String,
    val name: String,
    val songCount: Int,
    val duration: Int,
    val owner: String? = null,
    val public: Boolean? = null,
    val coverArt: String? = null
)

data class PlaylistDetails(
    val id: String,
    val name: String,
    val entry: List<SongDto>? = null
)

// Genres
data class GenresContainer(
    val genre: List<GenreDto>? = null
)

data class GenreDto(
    val value: String,
    val songCount: Int? = null,
    val albumCount: Int? = null
)

data class SongsByGenreContainer(
    val song: List<SongDto>? = null
)