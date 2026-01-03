# Documentación de Funciones y Componentes

## Tabla de Contenidos

1. [ViewModels](#viewmodels)
2. [Repositories](#repositories)
3. [Workers](#workers)
4. [Services](#services)
5. [DAOs](#daos)
6. [Utilidades](#utilidades)

## ViewModels

### AlbumDetailViewModel

**Propósito:** Gestionar el estado y lógica de negocio de la pantalla de detalle de álbum.

**Dependencias:**
- `musicRepository: MusicRepository` - Acceso a datos de música
- `musicController: MusicController` - Control de reproducción
- `serverDao: ServerDao` - Acceso a información del servidor
- `appContext: Context` - Contexto de la aplicación

**Estados:**

```kotlin
private val _album = MutableStateFlow<AlbumDto?>(null)
val album: StateFlow<AlbumDto?> = _album.asStateFlow()

private val _songs = MutableStateFlow<List<SongDto>>(emptyList())
val songs: StateFlow<List<SongDto>> = _songs.asStateFlow()

private val _isLoading = MutableStateFlow(true)
val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
```

**Funciones Principales:**

#### loadAlbum

```kotlin
fun loadAlbum(albumId: String)
```

**Parámetros:**
- `albumId`: Identificador único del álbum

**Descripción:** Carga los datos del álbum desde el servidor y actualiza el estado.

**Flujo:**
1. Marca isLoading como true
2. Obtiene servidor activo
3. Llama a la API para obtener datos del álbum
4. Actualiza estados _album y _songs
5. Marca isLoading como false

#### playAlbum

```kotlin
fun playAlbum()
```

**Descripción:** Reproduce todas las canciones del álbum en orden.

**Flujo:**
1. Obtiene lista de canciones del álbum
2. Crea MediaItems para cada canción
3. Establece la cola de reproducción en MusicController
4. Inicia reproducción

#### shufflePlay

```kotlin
fun shufflePlay()
```

**Descripción:** Reproduce las canciones del álbum en orden aleatorio.

**Flujo:**
1. Obtiene lista de canciones
2. Mezcla aleatoriamente la lista
3. Crea MediaItems
4. Establece cola y reproduce

#### downloadSong

```kotlin
fun downloadSong(song: SongDto)
```

**Parámetros:**
- `song`: Canción a descargar

**Descripción:** Encola un Worker para descargar una canción específica.

**Flujo:**
1. Obtiene servidor activo
2. Crea InputData con información de la canción y servidor
3. Crea OneTimeWorkRequest
4. Encola el trabajo en WorkManager

#### downloadAlbum

```kotlin
fun downloadAlbum()
```

**Descripción:** Descarga todas las canciones del álbum usando estrategia híbrida de batches.

**Estrategia de Batches:**
- Divide canciones en grupos de 10
- Procesa batches en paralelo
- Batches se ejecutan secuencialmente

**Código:**

```kotlin
val batchSize = 10
val batches = songs.chunked(batchSize)
var continuation: WorkContinuation? = null

batches.forEach { batch ->
    val parallelWorks = batch.map { song ->
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(createInputData(song))
            .build()
    }
    
    continuation = if (continuation == null) {
        WorkManager.getInstance(appContext).beginWith(parallelWorks)
    } else {
        continuation!!.then(parallelWorks)
    }
}

continuation?.enqueue()
```

#### addToFavorites

```kotlin
fun addToFavorites(songIds: Set<String>)
```

**Parámetros:**
- `songIds`: Conjunto de IDs de canciones a marcar como favoritas

**Descripción:** Marca canciones como favoritas local y remotamente usando batch operation.

**Flujo:**
1. Itera sobre songIds y llama a `musicRepository.addToFavorites()`
2. Sincroniza con servidor usando `api.star()` en una sola llamada
3. Recarga datos del álbum para reflejar cambios

**Optimización:** Usa batch operation en lugar de N llamadas individuales.

#### playSong

```kotlin
fun playSong(song: SongDto)
```

**Parámetros:**
- `song`: Canción a reproducir

**Descripción:** Reproduce una canción específica y establece las demás como cola.

**Flujo:**
1. Encuentra el índice de la canción en la lista
2. Reordena la lista con la canción seleccionada al principio
3. Crea MediaItems
4. Establece cola y reproduce

### DownloadViewModel

**Propósito:** Gestionar el estado de la pantalla de descargas y filtrado de contenido offline.

**Dependencias:**
- `musicRepository: MusicRepository`
- `playlistRepository: PlaylistRepository`
- `musicController: MusicController`

**Estados:**

```kotlin
private val _downloadedSongs = MutableStateFlow<List<SongEntity>>(emptyList())
val downloadedSongs: StateFlow<List<SongEntity>> = _downloadedSongs.asStateFlow()

private val _downloadedPlaylists = MutableStateFlow<List<PlaylistWithSongs>>(emptyList())
val downloadedPlaylists: StateFlow<List<PlaylistWithSongs>> = _downloadedPlaylists.asStateFlow()

private val _selectedPlaylistId = MutableStateFlow<String?>(null)
val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()
```

**Funciones Principales:**

#### loadDownloadedContent

```kotlin
fun loadDownloadedContent()
```

**Descripción:** Carga todas las canciones descargadas y playlists con canciones descargadas.

**Flujo:**
1. Recolecta Flow de canciones descargadas de Room
2. Filtra playlists que tengan al menos 1 canción descargada
3. Actualiza estados

**Lógica de Filtrado de Playlists:**

```kotlin
playlists.filter { playlistWithSongs ->
    playlistWithSongs.songs.any { song -> song.isDownloaded }
}
```

#### groupedSongs

```kotlin
val groupedSongs: StateFlow<Map<Char, List<SongEntity>>>
```

**Descripción:** Agrupa canciones alfabéticamente para mostrar sticky headers.

**Lógica:**
- Si hay playlist seleccionada: muestra TODAS las canciones de esa playlist
- Si no hay playlist: muestra SOLO canciones descargadas
- Agrupa por primera letra del título

**Código:**

```kotlin
combine(downloadedSongs, downloadedPlaylists, selectedPlaylistId) { songs, playlists, playlistId ->
    val songsToShow = if (playlistId != null) {
        // Mostrar TODAS las canciones de la playlist (descargadas y no descargadas)
        playlists.find { it.playlist.id == playlistId }?.songs ?: emptyList()
    } else {
        // Mostrar SOLO canciones descargadas
        songs.filter { it.isDownloaded }
    }
    
    songsToShow.groupBy { it.title.first().uppercaseChar() }
        .toSortedMap()
}
```

#### selectPlaylist

```kotlin
fun selectPlaylist(playlistId: String)
```

**Parámetros:**
- `playlistId`: ID de la playlist a filtrar

**Descripción:** Filtra las canciones mostradas por una playlist específica.

#### clearPlaylistFilter

```kotlin
fun clearPlaylistFilter()
```

**Descripción:** Limpia el filtro de playlist y muestra todas las canciones descargadas.

### HomeViewModel

**Propósito:** Gestionar el estado de la pantalla principal con álbumes recientes y random mix.

**Dependencias:**
- `musicRepository: MusicRepository`
- `musicController: MusicController`
- `serverDao: ServerDao`

**Estados:**

```kotlin
private val _recentAlbums = MutableStateFlow<List<AlbumDto>>(emptyList())
val recentAlbums: StateFlow<List<AlbumDto>> = _recentAlbums.asStateFlow()

private val _randomSongs = MutableStateFlow<List<SongDto>>(emptyList())
val randomSongs: StateFlow<List<SongDto>> = _randomSongs.asStateFlow()
```

**Funciones Principales:**

#### loadHomeData

```kotlin
fun loadHomeData()
```

**Descripción:** Carga datos iniciales de la pantalla home (álbumes recientes y canciones aleatorias).

**Flujo:**
1. Obtiene servidor activo
2. Llama a `api.getAlbumList()` con tipo "newest"
3. Llama a `api.getRandomSongs()` para el carrusel
4. Actualiza estados

#### playShuffle

```kotlin
fun playShuffle()
```

**Descripción:** Reproduce el mix aleatorio de canciones del carrusel.

#### refresh

```kotlin
suspend fun refresh()
```

**Descripción:** Recarga los datos de la pantalla con pull-to-refresh.

## Repositories

### MusicRepository

**Propósito:** Abstracción de acceso a datos de música, coordinando fuentes locales y remotas.

**Dependencias:**
- `api: NavidromeApiService`
- `songDao: SongDao`
- `serverDao: ServerDao`

**Funciones Principales:**

#### getAlbum

```kotlin
suspend fun getAlbum(albumId: String): AlbumDto?
```

**Parámetros:**
- `albumId`: ID del álbum

**Retorna:** AlbumDto o null si no se encuentra

**Flujo:**
1. Obtiene servidor activo
2. Llama a `api.getAlbum()`
3. Retorna datos sin cachear (siempre frescos del servidor)

#### getSongsByAlbum

```kotlin
suspend fun getSongsByAlbum(albumId: String): List<SongDto>
```

**Parámetros:**
- `albumId`: ID del álbum

**Retorna:** Lista de canciones del álbum

**Flujo:**
1. Obtiene servidor activo
2. Llama a `api.getAlbum()` para obtener el álbum completo
3. Extrae y retorna la lista de canciones

#### downloadSong

```kotlin
suspend fun downloadSong(song: SongDto, server: ServerEntity): Boolean
```

**Parámetros:**
- `song`: Canción a descargar
- `server`: Información del servidor

**Retorna:** Boolean indicando éxito

**Flujo:**
1. Construye URL de descarga con autenticación
2. Realiza petición HTTP para descargar archivo
3. Guarda archivo en almacenamiento interno
4. Actualiza Room Database (isDownloaded = true, path = rutaArchivo)

**Código:**

```kotlin
val url = buildDownloadUrl(song.id, server)
val response = api.downloadSong(url)

if (response.isSuccessful) {
    val inputStream = response.body()?.byteStream()
    val file = File(context.filesDir, "music/${song.id}.mp3")
    
    inputStream?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    
    songDao.updateDownloadStatus(song.id, true, file.absolutePath)
    return true
}

return false
```

#### addToFavorites

```kotlin
suspend fun addToFavorites(songId: String)
```

**Parámetros:**
- `songId`: ID de la canción

**Descripción:** Marca una canción como favorita en la base de datos local.

**Flujo:**
1. Inserta en tabla de favoritos
2. Actualiza flag isFavorite en song entity

### PlaylistRepository

**Propósito:** Gestión de playlists y relaciones con canciones.

**Dependencias:**
- `api: NavidromeApiService`
- `playlistDao: PlaylistDao`
- `serverDao: ServerDao`

**Funciones Principales:**

#### getPlaylistsWithSongs

```kotlin
fun getPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>
```

**Retorna:** Flow de playlists con sus canciones

**Descripción:** Observa cambios en playlists y sus canciones usando Room relations.

**Room Relation:**

```kotlin
data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)
```

#### syncPlaylists

```kotlin
suspend fun syncPlaylists()
```

**Descripción:** Sincroniza playlists del servidor con la base de datos local.

**Flujo:**
1. Obtiene servidor activo
2. Llama a `api.getPlaylists()`
3. Para cada playlist, obtiene detalles completos
4. Inserta en Room Database
5. Crea relaciones playlist-song en tabla junction

## Workers

### DownloadWorker

**Propósito:** Worker de WorkManager para descargar canciones en segundo plano.

**Parámetros de Entrada (InputData):**

```kotlin
const val KEY_SONG_ID = "song_id"
const val KEY_SONG_TITLE = "song_title"
const val KEY_SERVER_URL = "server_url"
const val KEY_USERNAME = "username"
const val KEY_TOKEN = "token"
const val KEY_SALT = "salt"
```

**Función Principal:**

#### doWork

```kotlin
override suspend fun doWork(): Result
```

**Retorna:** Result.success(), Result.retry(), o Result.failure()

**Flujo:**
1. Extrae parámetros de InputData
2. Llama a `musicRepository.downloadSong()`
3. Actualiza contador atómico de progreso
4. Muestra notificación de progreso
5. Retorna resultado

**Notificación de Progreso:**

```kotlin
val progress = DownloadProgress.incrementAndGet(workerId)
val notification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle("Descargando música")
    .setContentText("$progress canciones descargadas")
    .setSmallIcon(R.drawable.ic_download)
    .setProgress(totalSongs, progress, false)
    .build()

setForeground(ForegroundInfo(NOTIFICATION_ID, notification))
```

**Estrategia de Reintentos:**

```kotlin
OneTimeWorkRequestBuilder<DownloadWorker>()
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        WorkRequest.MIN_BACKOFF_MILLIS,
        TimeUnit.MILLISECONDS
    )
    .build()
```

### DownloadProgress

**Propósito:** Singleton para gestionar contadores atómicos de progreso de descargas.

**Estructura:**

```kotlin
object DownloadProgress {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()
    
    fun initCounter(workerId: String, initial: Int = 0) {
        counters[workerId] = AtomicInteger(initial)
    }
    
    fun incrementAndGet(workerId: String): Int {
        return counters[workerId]?.incrementAndGet() ?: 0
    }
    
    fun reset(workerId: String) {
        counters.remove(workerId)
    }
}
```

**Características:**
- Thread-safe con ConcurrentHashMap
- Operaciones atómicas con AtomicInteger
- Múltiples contadores simultáneos por workerId

## Services

### PlayerService

**Propósito:** Servicio de reproducción de audio en segundo plano.

**Herencia:** MediaSessionService (Media3)

**Dependencias:**
- `musicRepository: MusicRepository`
- `serverDao: ServerDao`

**Variables de Estado:**

```kotlin
private lateinit var player: ExoPlayer
private lateinit var mediaSession: MediaSession
private val queue = mutableListOf<MediaItem>()
private var currentIndex = 0
```

**Funciones Principales:**

#### onCreate

```kotlin
override fun onCreate()
```

**Descripción:** Inicializa el reproductor y la sesión de medios.

**Flujo:**
1. Crea instancia de ExoPlayer
2. Configura listeners de eventos
3. Crea MediaSession
4. Registra sesión con MediaSessionService

#### onGetSession

```kotlin
override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession?
```

**Retorna:** MediaSession para el controlador

**Descripción:** Provee la sesión de medios a controladores externos (notificación, Android Auto, etc.)

#### setQueue

```kotlin
fun setQueue(items: List<MediaItem>, startIndex: Int = 0)
```

**Parámetros:**
- `items`: Lista de MediaItems a reproducir
- `startIndex`: Índice de inicio

**Descripción:** Establece una nueva cola de reproducción.

**Flujo:**
1. Limpia cola actual
2. Agrega nuevos items
3. Prepara reproductor
4. Empieza reproducción desde startIndex

#### play/pause/next/previous

```kotlin
fun play()
fun pause()
fun next()
fun previous()
```

**Descripción:** Controles básicos de reproducción.

#### seekTo

```kotlin
fun seekTo(position: Long)
```

**Parámetros:**
- `position`: Posición en milisegundos

**Descripción:** Salta a una posición específica en la canción actual.

#### updateNotification

```kotlin
private fun updateNotification()
```

**Descripción:** Actualiza la notificación de reproducción con información actual.

**Componentes de Notificación:**
- Artwork de la canción
- Título y artista
- Botones de control (prev, play/pause, next)
- Tiempo de reproducción
- Barra de progreso

## DAOs

### SongDao

**Propósito:** Operaciones de base de datos para canciones.

**Queries Principales:**

#### getAllDownloadedSongs

```kotlin
@Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
fun getAllDownloadedSongs(): Flow<List<SongEntity>>
```

**Retorna:** Flow de canciones descargadas ordenadas alfabéticamente

#### getSongsByAlbum

```kotlin
@Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY trackNumber ASC")
suspend fun getSongsByAlbum(albumId: String): List<SongEntity>
```

**Parámetros:**
- `albumId`: ID del álbum

**Retorna:** Lista de canciones del álbum ordenadas por número de track

#### updateDownloadStatus

```kotlin
@Query("UPDATE songs SET isDownloaded = :isDownloaded, path = :path WHERE id = :songId")
suspend fun updateDownloadStatus(songId: String, isDownloaded: Boolean, path: String?)
```

**Parámetros:**
- `songId`: ID de la canción
- `isDownloaded`: Estado de descarga
- `path`: Ruta del archivo local

**Descripción:** Actualiza el estado de descarga de una canción.

#### insertAll

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAll(songs: List<SongEntity>)
```

**Parámetros:**
- `songs`: Lista de canciones a insertar

**Descripción:** Inserta o actualiza múltiples canciones en una transacción.

### PlaylistDao

**Propósito:** Operaciones de base de datos para playlists.

**Queries Principales:**

#### getPlaylistsWithSongs

```kotlin
@Transaction
@Query("SELECT * FROM playlists ORDER BY name ASC")
fun getPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>
```

**Retorna:** Flow de playlists con sus canciones relacionadas

**Nota:** @Transaction asegura que la relación se resuelva de forma atómica

#### addSongToPlaylist

```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)
```

**Parámetros:**
- `crossRef`: Referencia de relación playlist-canción

**Descripción:** Agrega una canción a una playlist (relación N:M)

#### removeSongFromPlaylist

```kotlin
@Delete
suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)
```

**Parámetros:**
- `crossRef`: Referencia de relación a eliminar

**Descripción:** Elimina una canción de una playlist

## Utilidades

### MD5Utils

**Propósito:** Funciones para autenticación Subsonic/Navidrome.

#### generateToken

```kotlin
fun generateToken(password: String, salt: String): String
```

**Parámetros:**
- `password`: Contraseña del usuario
- `salt`: Salt aleatorio

**Retorna:** Hash MD5 de password + salt

**Algoritmo:**
1. Concatena password y salt
2. Calcula MD5 hash
3. Convierte a representación hexadecimal

**Código:**

```kotlin
val md = MessageDigest.getInstance("MD5")
val digest = md.digest("$password$salt".toByteArray())
return digest.joinToString("") { "%02x".format(it) }
```

#### generateSalt

```kotlin
fun generateSalt(): String
```

**Retorna:** String aleatorio de 6 caracteres

**Descripción:** Genera un salt aleatorio para autenticación.

### FormatUtils

**Propósito:** Funciones de formateo para la UI.

#### formatDuration

```kotlin
fun formatDuration(seconds: Int): String
```

**Parámetros:**
- `seconds`: Duración en segundos

**Retorna:** String en formato "MM:SS" o "HH:MM:SS"

**Ejemplos:**
- 65 -> "1:05"
- 3665 -> "1:01:05"

#### formatFileSize

```kotlin
fun formatFileSize(bytes: Long): String
```

**Parámetros:**
- `bytes`: Tamaño en bytes

**Retorna:** String legible (KB, MB, GB)

**Ejemplos:**
- 1024 -> "1.0 KB"
- 1048576 -> "1.0 MB"
- 1073741824 -> "1.0 GB"

### ImageUtils

**Propósito:** Funciones para manejo de imágenes.

#### getCoverUrl

```kotlin
fun getCoverUrl(coverArt: String?, server: ServerEntity): String?
```

**Parámetros:**
- `coverArt`: ID del cover art
- `server`: Información del servidor

**Retorna:** URL completa del cover art o null

**Código:**

```kotlin
if (coverArt == null) return null

return "${server.url}/rest/getCoverArt" +
    "?id=$coverArt" +
    "&size=500" +
    "&u=${server.username}" +
    "&t=${server.token}" +
    "&s=${server.salt}" +
    "&v=1.16.1" +
    "&c=NeoSynth" +
    "&f=json"
```

### NetworkUtils

**Propósito:** Utilidades de red y conectividad.

#### isNetworkAvailable

```kotlin
fun isNetworkAvailable(context: Context): Boolean
```

**Parámetros:**
- `context`: Contexto de Android

**Retorna:** Boolean indicando si hay conexión a internet

**Implementación:**

```kotlin
val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
val network = connectivityManager.activeNetwork ?: return false
val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
```
