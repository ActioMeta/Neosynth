# Stack Tecnológico

## Lenguajes y Frameworks

### Kotlin 2.0.21
**Propósito:** Lenguaje de programación principal

**Características Utilizadas:**
- Coroutines para programación asíncrona
- Flow para streams reactivos
- Null safety
- Extension functions
- Data classes
- Sealed classes para estados de UI
- Companion objects

**Ejemplo:**
```kotlin
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Int
)

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: List<Song>) : UiState()
    data class Error(val message: String) : UiState()
}
```

## UI Framework

### Jetpack Compose 1.7.6
**Propósito:** UI declarativa moderna

**Características:**
- Composables para componentes reutilizables
- State hoisting
- Remember y rememberSaveable
- LazyColumn para listas eficientes
- Animaciones declarativas

**Ejemplo:**
```kotlin
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        AsyncImage(
            model = song.coverArt,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
            Text(text = song.artist, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

### Material Design 3 (Material You) 1.3.1
**Propósito:** Sistema de diseño

**Componentes Usados:**
- `Scaffold`: Estructura base de pantallas
- `TopAppBar`: Barra superior con título y acciones
- `FloatingActionButton`: FAB expandible para filtros
- `Card`: Tarjetas para álbumes y canciones
- `IconButton`: Botones de íconos para controles
- `Slider`: Barra de progreso de reproducción
- `Surface`: Contenedores con elevación
- `NavigationBar`: Barra de navegación inferior

**Theme Dinámico:**
```kotlin
@Composable
fun NeoSynthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

## Inyección de Dependencias

### Hilt 2.51.1
**Propósito:** Dependency Injection basado en Dagger

**Características:**
- `@HiltAndroidApp` para Application
- `@AndroidEntryPoint` para Activities, Fragments, Services
- `@HiltViewModel` para ViewModels
- `@Singleton` para dependencias únicas
- `@HiltWorker` para Workers

**Módulos:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://demo.navidrome.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
```

### KSP (Kotlin Symbol Processing) 2.0.21-1.0.29
**Propósito:** Procesamiento de anotaciones para Hilt y Room

**Ventajas sobre KAPT:**
- 2x más rápido que kapt
- Menor uso de memoria
- Mejor integración con Kotlin

## Base de Datos

### Room 2.6.1
**Propósito:** Base de datos SQLite con ORM

**Componentes:**
- `@Database`: Clase principal de base de datos
- `@Entity`: Tablas
- `@Dao`: Data Access Objects
- `@Query`: Consultas SQL tipadas
- `@Transaction`: Operaciones atómicas
- `@Relation`: Relaciones entre entidades

**Entidades:**
```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val path: String,
    val isDownloaded: Boolean = false,
    val serverId: Long
)

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int
)
```

**DAOs con Flow:**
```kotlin
@Dao
interface MusicDao {
    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    fun getDownloadedSongs(): Flow<List<SongEntity>>
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithSongs(playlistId: String): PlaylistWithSongs?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)
}
```

## Networking

### Retrofit 2.11.0
**Propósito:** Cliente HTTP para API REST

**Configuración:**
```kotlin
interface NavidromeApi {
    @GET("rest/search3.view")
    suspend fun search(
        @Query("query") query: String,
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "NeoSynth",
        @Query("f") format: String = "json"
    ): SearchResponse
    
    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList(
        @Query("type") type: String = "newest",
        @Query("size") size: Int = 20,
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "NeoSynth",
        @Query("f") format: String = "json"
    ): AlbumListResponse
}
```

### OkHttp 4.12.0
**Propósito:** Cliente HTTP subyacente para Retrofit

**Interceptors:**
```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
```

### Gson 2.10.1
**Propósito:** Serialización/deserialización JSON

**Modelos:**
```kotlin
data class AlbumDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("coverArt") val coverArt: String?,
    @SerializedName("songCount") val songCount: Int
)
```

## Reproducción de Audio

### Media3 ExoPlayer 1.5.0
**Propósito:** Reproducción de audio/video

**Características:**
- Soporte para múltiples formatos (MP3, FLAC, OGG, etc.)
- Streaming adaptativo
- Gapless playback
- MediaSession integration
- Notificaciones de media

**Configuración:**
```kotlin
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    
    override fun onCreate() {
        super.onCreate()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()
    }
}
```

### MediaSession 1.2.2
**Propósito:** Control de media del sistema

**Características:**
- Controles en lockscreen
- Integración con Bluetooth/Auto
- Comandos de media system-wide

## Tareas en Background

### WorkManager 2.9.1
**Propósito:** Tareas en segundo plano garantizadas

**Workers:**
```kotlin
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MusicRepository,
    private val api: NavidromeApi
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val songId = inputData.getString("songId") ?: return Result.failure()
        
        return try {
            val audioFile = downloadAudioFile(songId)
            val coverFile = downloadCoverArt(songId)
            
            repository.insertSong(
                SongEntity(
                    id = songId,
                    path = audioFile.absolutePath,
                    imageUrl = coverFile?.absolutePath,
                    isDownloaded = true
                )
            )
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
```

**Enqueue:**
```kotlin
val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
    .setInputData(
        Data.Builder()
            .putString("songId", song.id)
            .build()
    )
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .build()

WorkManager.getInstance(context).enqueue(downloadRequest)
```

## Carga de Imágenes

### Coil 2.7.0
**Propósito:** Carga asíncrona de imágenes

**Características:**
- Cache automático (memoria + disco)
- Transformaciones (crop, rounded corners)
- Placeholders y error images
- Integración nativa con Compose

**Uso:**
```kotlin
@Composable
fun AlbumCover(imageUrl: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.placeholder_album),
        error = painterResource(R.drawable.error_album)
    )
}
```

## Programación Asíncrona

### Kotlin Coroutines 1.8.1
**Propósito:** Concurrencia y programación asíncrona

**Dispatchers:**
- `Dispatchers.Main`: Operaciones de UI
- `Dispatchers.IO`: Operaciones de I/O (red, disco)
- `Dispatchers.Default`: Cálculos pesados

**ViewModelScope:**
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    
    fun loadAlbums() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            
            try {
                val albums = withContext(Dispatchers.IO) {
                    repository.getRecentAlbums()
                }
                _state.value = UiState.Success(albums)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

### Flow 1.8.1
**Propósito:** Streams reactivos de datos

**Operadores:**
```kotlin
fun getDownloadedSongs(): Flow<List<Song>> {
    return dao.getDownloadedSongs()
        .map { entities -> entities.map { it.toDomain() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
}

@Composable
fun DownloadScreen(viewModel: DownloadViewModel) {
    val songs by viewModel.downloadedSongs.collectAsState()
    
    LazyColumn {
        items(songs) { song ->
            SongItem(song = song)
        }
    }
}
```

## Navegación

### Navigation Compose 2.8.5
**Propósito:** Navegación entre pantallas

**NavGraph:**
```kotlin
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(navController, startDestination) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        
        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId")
            AlbumDetailScreen(albumId, navController)
        }
    }
}
```

## Testing

### JUnit 4.13.2
**Propósito:** Unit testing framework

### Mockito 5.7.0
**Propósito:** Mocking para tests

### Coroutines Test 1.8.1
**Propósito:** Testing de coroutines

**Ejemplo:**
```kotlin
@Test
fun `load albums should emit success state`() = runTest {
    // Given
    val albums = listOf(Album("1", "Abbey Road", "The Beatles"))
    coEvery { repository.getRecentAlbums() } returns albums
    
    // When
    viewModel.loadAlbums()
    
    // Then
    assertEquals(UiState.Success(albums), viewModel.state.value)
}
```

## Build System

### Gradle 8.13
**Propósito:** Build automation

**Versiones Centralizadas (libs.versions.toml):**
```toml
[versions]
kotlin = "2.0.21"
compose = "1.7.6"
material3 = "1.3.1"
hilt = "2.51.1"
room = "2.6.1"
retrofit = "2.11.0"

[libraries]
androidx-compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
```

## Plugins

### Android Gradle Plugin 8.7.3
**Propósito:** Build de aplicaciones Android

### Kotlin Gradle Plugin 2.0.21
**Propósito:** Compilación de Kotlin

### Compose Compiler Plugin 2.0.21
**Propósito:** Compilador de Jetpack Compose

## Resumen de Dependencias

| Categoría | Librería | Versión | Propósito |
|-----------|----------|---------|-----------|
| Lenguaje | Kotlin | 2.0.21 | Lenguaje principal |
| UI | Jetpack Compose | 1.7.6 | Framework de UI |
| Design | Material 3 | 1.3.1 | Sistema de diseño |
| DI | Hilt | 2.51.1 | Inyección de dependencias |
| Database | Room | 2.6.1 | ORM local |
| Network | Retrofit | 2.11.0 | Cliente HTTP |
| HTTP Client | OkHttp | 4.12.0 | Cliente HTTP base |
| JSON | Gson | 2.10.1 | Serialización |
| Media | Media3 ExoPlayer | 1.5.0 | Reproducción audio |
| Background | WorkManager | 2.9.1 | Tareas background |
| Images | Coil | 2.7.0 | Carga de imágenes |
| Async | Coroutines | 1.8.1 | Programación asíncrona |
| Navigation | Navigation Compose | 2.8.5 | Navegación |
| Annotation | KSP | 2.0.21-1.0.29 | Procesamiento |
