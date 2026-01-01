package com.example.neosynth.data.local.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.example.neosynth.data.local.entities.AlbumEntity
import com.example.neosynth.data.local.entities.ArtistEntity
import com.example.neosynth.data.local.entities.SongEntity

data class ArtistWithAlbums(
    @Embedded val artist: ArtistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "artistId"
    )
    val albums: List<AlbumEntity>
)

data class AlbumWithSongs(
    @Embedded val album: AlbumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "albumId"
    )
    val songs: List<SongEntity>
)