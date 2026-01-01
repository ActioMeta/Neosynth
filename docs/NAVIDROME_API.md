# API de Navidrome

## Introducción

Navidrome implementa la API de Subsonic, que es un estándar de facto para servidores de música personal. NeoSynth utiliza esta API para comunicarse con el servidor.

## Autenticación

### Método Token + Salt (Recomendado)

Navidrome soporta autenticación mediante token MD5, que es más segura que enviar la contraseña en texto plano.

**Generación del Token:**
```kotlin
fun generateAuthParams(username: String, password: String): Map<String, String> {
    val salt = UUID.randomUUID().toString().replace("-", "")
    val token = md5("$password$salt")
    
    return mapOf(
        "u" to username,
        "t" to token,
        "s" to salt,
        "v" to "1.16.1",
        "c" to "NeoSynth",
        "f" to "json"
    )
}

fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
```

**Parámetros Comunes:**
- `u`: Username
- `t`: Token MD5 (md5(password + salt))
- `s`: Salt aleatorio
- `v`: Versión de la API (1.16.1)
- `c`: Nombre del cliente (NeoSynth)
- `f`: Formato de respuesta (json)

## Endpoints Utilizados

### System

#### ping.view
**Propósito:** Verificar conectividad con el servidor

**Request:**
```
GET /rest/ping.view?u=usuario&t=token&s=salt&v=1.16.1&c=NeoSynth&f=json
```

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1"
  }
}
```

**Implementación:**
```kotlin
@GET("rest/ping.view")
suspend fun ping(
    @Query("u") username: String,
    @Query("t") token: String,
    @Query("s") salt: String,
    @Query("v") version: String = "1.16.1",
    @Query("c") client: String = "NeoSynth",
    @Query("f") format: String = "json"
): PingResponse
```

### Browsing

#### getAlbumList2.view
**Propósito:** Obtener lista de álbumes por diferentes criterios

**Request:**
```
GET /rest/getAlbumList2.view?type=newest&size=20&offset=0&u=...&t=...&s=...
```

**Parámetros:**
- `type`: Tipo de lista (newest, recent, frequent, random, alphabeticalByName, alphabeticalByArtist, starred)
- `size`: Número de álbumes (default: 10, max: 500)
- `offset`: Offset para paginación

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "albumList2": {
      "album": [
        {
          "id": "al-1",
          "name": "Abbey Road",
          "artist": "The Beatles",
          "artistId": "ar-1",
          "coverArt": "al-1",
          "songCount": 17,
          "duration": 2873,
          "created": "2024-01-15T10:30:00.000Z",
          "year": 1969,
          "genre": "Rock"
        }
      ]
    }
  }
}
```

**Implementación:**
```kotlin
@GET("rest/getAlbumList2.view")
suspend fun getAlbumList(
    @Query("type") type: String = "newest",
    @Query("size") size: Int = 20,
    @Query("offset") offset: Int = 0,
    @Query("u") username: String,
    @Query("t") token: String,
    @Query("s") salt: String,
    @Query("v") version: String = "1.16.1",
    @Query("c") client: String = "NeoSynth",
    @Query("f") format: String = "json"
): AlbumListResponse
```

#### getAlbum.view
**Propósito:** Obtener detalles de un álbum específico con sus canciones

**Request:**
```
GET /rest/getAlbum.view?id=al-1&u=...&t=...&s=...
```

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "album": {
      "id": "al-1",
      "name": "Abbey Road",
      "artist": "The Beatles",
      "artistId": "ar-1",
      "coverArt": "al-1",
      "songCount": 17,
      "duration": 2873,
      "year": 1969,
      "song": [
        {
          "id": "tr-1",
          "title": "Come Together",
          "album": "Abbey Road",
          "albumId": "al-1",
          "artist": "The Beatles",
          "artistId": "ar-1",
          "track": 1,
          "year": 1969,
          "genre": "Rock",
          "coverArt": "al-1",
          "size": 4571392,
          "contentType": "audio/mpeg",
          "suffix": "mp3",
          "duration": 259,
          "bitRate": 320,
          "path": "The Beatles/Abbey Road/01 Come Together.mp3"
        }
      ]
    }
  }
}
```

#### getPlaylists.view
**Propósito:** Obtener lista de todas las playlists

**Request:**
```
GET /rest/getPlaylists.view?u=...&t=...&s=...
```

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "playlists": {
      "playlist": [
        {
          "id": "pl-1",
          "name": "My Favorites",
          "songCount": 42,
          "duration": 10800,
          "created": "2024-01-10T15:00:00.000Z",
          "changed": "2024-01-20T18:30:00.000Z",
          "coverArt": "pl-1"
        }
      ]
    }
  }
}
```

#### getPlaylist.view
**Propósito:** Obtener detalles de una playlist con sus canciones

**Request:**
```
GET /rest/getPlaylist.view?id=pl-1&u=...&t=...&s=...
```

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "playlist": {
      "id": "pl-1",
      "name": "My Favorites",
      "songCount": 2,
      "duration": 518,
      "entry": [
        {
          "id": "tr-1",
          "title": "Come Together",
          "artist": "The Beatles",
          "duration": 259
        },
        {
          "id": "tr-2",
          "title": "Something",
          "artist": "The Beatles",
          "duration": 259
        }
      ]
    }
  }
}
```

### Searching

#### search3.view
**Propósito:** Búsqueda unificada de artistas, álbumes y canciones

**Request:**
```
GET /rest/search3.view?query=beatles&artistCount=10&albumCount=20&songCount=50&u=...&t=...&s=...
```

**Parámetros:**
- `query`: Término de búsqueda
- `artistCount`: Máximo de artistas (default: 20)
- `albumCount`: Máximo de álbumes (default: 20)
- `songCount`: Máximo de canciones (default: 20)

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok",
    "searchResult3": {
      "artist": [
        {
          "id": "ar-1",
          "name": "The Beatles",
          "coverArt": "ar-1",
          "albumCount": 13
        }
      ],
      "album": [
        {
          "id": "al-1",
          "name": "Abbey Road",
          "artist": "The Beatles",
          "coverArt": "al-1"
        }
      ],
      "song": [
        {
          "id": "tr-1",
          "title": "Come Together",
          "album": "Abbey Road",
          "artist": "The Beatles",
          "duration": 259
        }
      ]
    }
  }
}
```

**Implementación:**
```kotlin
@GET("rest/search3.view")
suspend fun search(
    @Query("query") query: String,
    @Query("artistCount") artistCount: Int = 10,
    @Query("albumCount") albumCount: Int = 20,
    @Query("songCount") songCount: Int = 50,
    @Query("u") username: String,
    @Query("t") token: String,
    @Query("s") salt: String,
    @Query("v") version: String = "1.16.1",
    @Query("c") client: String = "NeoSynth",
    @Query("f") format: String = "json"
): SearchResponse
```

### Media Retrieval

#### stream.view
**Propósito:** Streaming o descarga de archivo de audio

**Request:**
```
GET /rest/stream.view?id=tr-1&maxBitRate=320&u=...&t=...&s=...
```

**Parámetros:**
- `id`: ID de la canción
- `maxBitRate`: Bitrate máximo (opcional, para transcoding)
- `format`: Formato de salida (opcional, para transcoding)

**Response:** Binary audio stream (audio/mpeg, audio/flac, etc.)

**Implementación:**
```kotlin
fun buildStreamUrl(
    baseUrl: String,
    songId: String,
    username: String,
    token: String,
    salt: String
): String {
    return "$baseUrl/rest/stream.view" +
        "?id=$songId" +
        "&u=$username" +
        "&t=$token" +
        "&s=$salt" +
        "&v=1.16.1" +
        "&c=NeoSynth"
}
```

**Uso con Media3:**
```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(buildStreamUrl(serverUrl, song.id, user, token, salt))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(buildCoverArtUrl(song.coverArt)))
            .build()
    )
    .build()
```

#### download.view
**Propósito:** Descarga de archivo de audio (sin transcoding)

Similar a `stream.view` pero siempre devuelve el archivo original:

```
GET /rest/download.view?id=tr-1&u=...&t=...&s=...
```

**Uso para Descargas:**
```kotlin
suspend fun downloadSong(songId: String): File {
    val url = buildDownloadUrl(serverUrl, songId, user, token, salt)
    val response = okHttpClient.newCall(
        Request.Builder().url(url).build()
    ).execute()
    
    val outputFile = File(context.getExternalFilesDir(null), "$songId.mp3")
    response.body?.byteStream()?.use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    
    return outputFile
}
```

#### getCoverArt.view
**Propósito:** Obtener imagen de carátula

**Request:**
```
GET /rest/getCoverArt.view?id=al-1&size=300&u=...&t=...&s=...
```

**Parámetros:**
- `id`: ID del coverArt (álbum, artista, o playlist)
- `size`: Tamaño en píxeles (opcional, para redimensionar)

**Response:** Binary image (image/jpeg, image/png)

**Uso con Coil:**
```kotlin
AsyncImage(
    model = buildCoverArtUrl(album.coverArt, size = 300),
    contentDescription = null,
    modifier = Modifier.size(300.dp)
)

fun buildCoverArtUrl(
    coverArtId: String?,
    size: Int? = null
): String {
    if (coverArtId == null) return ""
    
    var url = "$serverUrl/rest/getCoverArt.view" +
        "?id=$coverArtId" +
        "&u=$username" +
        "&t=$token" +
        "&s=$salt" +
        "&v=1.16.1" +
        "&c=NeoSynth"
    
    if (size != null) {
        url += "&size=$size"
    }
    
    return url
}
```

### User Management

#### star.view
**Propósito:** Marcar como favorito (star)

**Request:**
```
POST /rest/star.view?id=tr-1&u=...&t=...&s=...
```

**Parámetros:**
- `id`: ID del item (canción, álbum, o artista)
- Soporta múltiples IDs: `id=tr-1&id=tr-2&id=tr-3`

**Response:**
```json
{
  "subsonic-response": {
    "status": "ok"
  }
}
```

**Implementación:**
```kotlin
@POST("rest/star.view")
suspend fun star(
    @Query("id") id: String,
    @Query("u") username: String,
    @Query("t") token: String,
    @Query("s") salt: String,
    @Query("v") version: String = "1.16.1",
    @Query("c") client: String = "NeoSynth",
    @Query("f") format: String = "json"
): BaseResponse
```

#### unstar.view
**Propósito:** Quitar de favoritos (unstar)

Igual que `star.view` pero elimina el favorito:

```
POST /rest/unstar.view?id=tr-1&u=...&t=...&s=...
```

## Manejo de Errores

### Códigos de Error Comunes

```json
{
  "subsonic-response": {
    "status": "failed",
    "error": {
      "code": 40,
      "message": "Wrong username or password"
    }
  }
}
```

**Códigos:**
- `10`: Required parameter is missing
- `20`: Incompatible Subsonic REST protocol version
- `30`: Incompatible Subsonic REST protocol version (server too old)
- `40`: Wrong username or password
- `41`: Token authentication not supported for LDAP users
- `50`: User is not authorized for the given operation
- `60`: The trial period for the Subsonic server is over
- `70`: The requested data was not found

**Implementación:**
```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
}

suspend fun <T> safeApiCall(
    call: suspend () -> SubsonicResponse<T>
): ApiResult<T> {
    return try {
        val response = call()
        if (response.subsonicResponse.status == "ok") {
            ApiResult.Success(response.subsonicResponse.data)
        } else {
            val error = response.subsonicResponse.error
            ApiResult.Error(error.code, error.message)
        }
    } catch (e: Exception) {
        ApiResult.Error(-1, e.message ?: "Unknown error")
    }
}
```

## Modelos de Datos

### DTOs (Data Transfer Objects)

```kotlin
data class SubsonicResponse<T>(
    @SerializedName("subsonic-response")
    val subsonicResponse: ResponseWrapper<T>
)

data class ResponseWrapper<T>(
    val status: String,
    val version: String,
    val error: ErrorResponse? = null,
    @SerializedName("albumList2")
    val albumList: AlbumList? = null,
    val album: AlbumDto? = null,
    @SerializedName("searchResult3")
    val searchResult: SearchResult? = null,
    val playlists: PlaylistsWrapper? = null,
    val playlist: PlaylistDto? = null
)

data class ErrorResponse(
    val code: Int,
    val message: String
)

data class AlbumDto(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val coverArt: String?,
    val songCount: Int,
    val duration: Int,
    val created: String,
    val year: Int?,
    val genre: String?,
    val song: List<SongDto>?
)

data class SongDto(
    val id: String,
    val title: String,
    val album: String,
    val albumId: String,
    val artist: String,
    val artistId: String,
    val track: Int?,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val size: Long,
    val contentType: String,
    val suffix: String,
    val duration: Int,
    val bitRate: Int?,
    val path: String
)
```

### Mappers (DTO -> Domain)

```kotlin
fun AlbumDto.toDomain(): Album {
    return Album(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        coverArt = coverArt,
        songCount = songCount,
        duration = duration,
        year = year,
        genre = genre
    )
}

fun SongDto.toEntity(serverId: Long): SongEntity {
    return SongEntity(
        id = id,
        title = title,
        serverID = serverId,
        artistID = artistId,
        artist = artist,
        albumID = albumId,
        album = album,
        duration = duration,
        imageUrl = coverArt,
        path = "",
        isDownloaded = false
    )
}
```

## Limitaciones y Consideraciones

### Rate Limiting
Navidrome no tiene rate limiting por defecto, pero es recomendable:
- Implementar debouncing para búsquedas
- Cachear resultados cuando sea posible
- Limitar peticiones concurrentes

### Tamaño de Respuesta
- `getAlbumList2` soporta máximo 500 items por request
- Usar paginación con `offset` para grandes bibliotecas

### Transcoding
- Navidrome soporta transcoding on-the-fly
- Usar `maxBitRate` en stream.view para limitar ancho de banda
- Considerar formato con `format` parameter (mp3, opus, etc.)

### Caché
- Implementar caché de carátulas para reducir requests
- Coil maneja caché automáticamente
- Room cachea datos localmente

## Recursos

- [Subsonic API Documentation](http://www.subsonic.org/pages/api.jsp)
- [Navidrome Documentation](https://www.navidrome.org/docs/)
- [Subsonic API Forum](https://www.subsonic.org/forum/)
