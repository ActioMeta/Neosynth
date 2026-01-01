# Arquitectura del Proyecto

## Visión General

NeoSynth sigue una arquitectura de tres capas basada en las recomendaciones de Android Architecture Components, con separación clara de responsabilidades y flujo de datos unidireccional.

## Diagrama de Capas

```
┌─────────────────────────────────────────────────────────────┐
│                         UI LAYER                            │
│  Jetpack Compose + Material 3 + Navigation                  │
│                                                             │
│  - Composables                                              │
│  - Screens                                                  │
│  - Navigation Graph                                         │
│  - UI State                                                 │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                      VIEWMODEL LAYER                        │
│  State Management + Business Logic                          │
│                                                             │
│  - ViewModels                                               │
│  - StateFlow / Flow                                         │
│  - Coroutines                                               │
│  - Use Cases (opcional)                                     │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                            │
│  Room (local) + Retrofit (remote) + WorkManager             │
│                                                             │
│  - Repositories                                             │
│  - Data Sources (Local/Remote)                              │
│  - DAOs                                                     │
│  - API Services                                             │
│  - Workers                                                  │
└─────────────────────────────────────────────────────────────┘
```

## Estructura de Carpetas

```
app/src/main/java/com/example/neosynth/
│
├── data/
│   ├── local/
│   │   ├── dao/
│   │   │   ├── MusicDao.kt              # Operaciones de canciones y playlists
│   │   │   └── ServerDao.kt             # Operaciones de servidores
│   │   ├── entities/
│   │   │   ├── SongEntity.kt            # Tabla de canciones
│   │   │   ├── PlaylistEntity.kt        # Tabla de playlists
│   │   │   ├── PlaylistSongCrossRef.kt  # Relación N:M
│   │   │   ├── PlaylistWithSongs.kt     # DTO de relación
│   │   │   └── ServerEntity.kt          # Tabla de servidores
│   │   └── MusicDatabase.kt             # Room Database
│   │
│   ├── remote/
│   │   ├── api/
│   │   │   └── NavidromeApi.kt          # Interface Retrofit
│   │   ├── dto/
│   │   │   ├── AlbumDto.kt              # Modelos de respuesta API
│   │   │   ├── SongDto.kt
│   │   │   └── PlaylistDto.kt
│   │   └── mapper/
│   │       └── EntityMappers.kt         # DTO -> Domain
│   │
│   ├── repository/
│   │   ├── MusicRepository.kt           # Coordinador de datos
│   │   └── ServerRepository.kt          # Gestión de servidores
│   │
│   └── worker/
│       └── DownloadWK.kt                # WorkManager para descargas
│
├── domain/
│   ├── model/
│   │   ├── Song.kt                      # Modelo de dominio
│   │   ├── Album.kt
│   │   ├── Artist.kt
│   │   └── Playlist.kt
│   │
│   └── provider/
│       └── MusicProvider.kt             # Interface de proveedor
│
├── player/
│   ├── MusicController.kt               # Controlador de reproducción
│   └── PlaybackService.kt               # MediaSessionService
│
├── ui/
│   ├── components/
│   │   ├── AlbumCard.kt                 # Componentes reutilizables
│   │   ├── MiniPlayer.kt
│   │   └── StickyHeader.kt
│   │
│   ├── home/
│   │   ├── HomeScreen.kt                # Pantalla principal
│   │   └── HomeViewModel.kt             # Lógica de home
│   │
│   ├── player/
│   │   ├── PlayerScreen.kt              # Reproductor completo
│   │   └── PlayerViewModel.kt
│   │
│   ├── downloads/
│   │   ├── DownloadScreen.kt            # Gestión de descargas
│   │   └── DownloadViewModel.kt
│   │
│   ├── album/
│   │   ├── AlbumDetailScreen.kt         # Detalle de álbum
│   │   └── AlbumDetailViewModel.kt
│   │
│   ├── playlist/
│   │   ├── PlaylistDetailScreen.kt      # Detalle de playlist
│   │   └── PlaylistDetailViewModel.kt
│   │
│   ├── login/
│   │   ├── LoginScreen.kt               # Autenticación
│   │   └── LoginViewModel.kt
│   │
│   ├── navigation/
│   │   ├── NavGraph.kt                  # Grafo de navegación
│   │   └── Screen.kt                    # Definición de rutas
│   │
│   └── theme/
│       ├── Color.kt                     # Paleta de colores M3
│       ├── Theme.kt                     # Composable de tema
│       └── Type.kt                      # Tipografía M3
│
├── depsInjection/
│   ├── AppModule.kt                     # Módulo principal Hilt
│   ├── DatabaseModule.kt                # Inyección de Room
│   ├── NetworkModule.kt                 # Inyección de Retrofit
│   └── RepositoryModule.kt              # Inyección de repositorios
│
├── receiver/
│   └── VoiceCommandReceiver.kt          # BroadcastReceiver Google Assistant
│
├── utils/
│   ├── AuthUtils.kt                     # Utilidades de autenticación
│   ├── FileUtils.kt                     # Gestión de archivos
│   └── PreferencesManager.kt            # SharedPreferences
│
└── MainActivity.kt                      # Punto de entrada
```

## Flujo de Datos

### 1. Flujo de Lectura (Streaming/Offline)

```
User Action (UI)
      │
      ▼
ViewModel observa State
      │
      ▼
Repository consulta
      │
      ├─────────────┬─────────────┐
      ▼             ▼             ▼
  Local (Room)  Remote (API)  Cache
      │             │             │
      └─────────────┴─────────────┘
                    │
                    ▼
          StateFlow emite datos
                    │
                    ▼
            UI se recompone
```

### 2. Flujo de Escritura (Descarga/Favoritos)

```
User Action (UI)
      │
      ▼
ViewModel dispara acción
      │
      ▼
Repository ejecuta operación
      │
      ├─────────────┬─────────────┐
      ▼             ▼             ▼
Guarda en Room  Envía a API  WorkManager
      │             │             │
      └─────────────┴─────────────┘
                    │
                    ▼
       Actualiza StateFlow
                    │
                    ▼
           UI se actualiza
```

## Patrones Utilizados

### Repository Pattern
Abstrae la fuente de datos (local o remota) del resto de la aplicación.

```kotlin
class MusicRepository @Inject constructor(
    private val musicDao: MusicDao,
    private val navidromeApi: NavidromeApi
) {
    fun getDownloadedSongs(): Flow<List<SongEntity>> {
        return musicDao.getDownloadedSongs()
    }
    
    suspend fun searchSongs(query: String): List<Song> {
        return navidromeApi.search(query).toSongs()
    }
}
```

### MVVM (Model-View-ViewModel)
Separa la lógica de UI de la lógica de negocio.

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()
    
    fun loadAlbums() {
        viewModelScope.launch {
            _albums.value = repository.getRecentAlbums()
        }
    }
}
```

### Dependency Injection (Hilt)
Proporciona dependencias automáticamente sin acoplamiento.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "neosynth_db"
        ).build()
    }
}
```

### Single Source of Truth (SSOT)
Room es la única fuente de verdad para datos locales.

```kotlin
suspend fun syncPlaylist(playlistId: String) {
    // 1. Obtener de API
    val playlistDto = api.getPlaylist(playlistId)
    
    // 2. Guardar en Room (SSOT)
    dao.insertPlaylist(playlistDto.toEntity())
    
    // 3. UI observa Flow desde Room automáticamente
}
```

## Inyección de Dependencias

### Módulos Hilt

**AppModule.kt** - Contexto y SharedPreferences
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context) = context
}
```

**DatabaseModule.kt** - Room Database y DAOs
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase
    
    @Provides
    fun provideMusicDao(db: MusicDatabase): MusicDao = db.musicDao()
}
```

**NetworkModule.kt** - Retrofit y OkHttp
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient
    
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit
    
    @Provides
    @Singleton
    fun provideNavidromeApi(retrofit: Retrofit): NavidromeApi
}
```

## Estado de UI

### StateFlow para Estado Reactivo

```kotlin
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    // Estado inmutable expuesto
    private val _downloadedSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val downloadedSongs: StateFlow<List<SongEntity>> = _downloadedSongs.asStateFlow()
    
    // Estado de UI
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            repository.getDownloadedSongs()
                .collect { songs ->
                    _downloadedSongs.value = songs
                    _uiState.value = UiState.Success
                }
        }
    }
}
```

### Sealed Class para Estados

```kotlin
sealed class UiState {
    object Loading : UiState()
    object Success : UiState()
    data class Error(val message: String) : UiState()
}
```

## Navegación

### Rutas Tipadas

```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Player : Screen("player")
    object Downloads : Screen("downloads")
    data class AlbumDetail(val albumId: String) : Screen("album/{albumId}") {
        fun createRoute(albumId: String) = "album/$albumId"
    }
}
```

### NavGraph

```kotlin
@Composable
fun NavGraph(
    navController: NavHostController,
    musicController: MusicController
) {
    NavHost(navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController, musicController)
        }
        composable(
            route = Screen.AlbumDetail().route,
            arguments = listOf(navArgument("albumId") { type = NavType.StringType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId")
            AlbumDetailScreen(albumId, navController)
        }
    }
}
```

## Gestión de Reproducción

### Media3 ExoPlayer

```kotlin
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
}
```

### MusicController

```kotlin
class MusicController @Inject constructor(
    private val context: Context
) {
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java)
    )
    
    private val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
    
    fun playQueue(songs: List<SongEntity>) {
        controllerFuture.get()?.apply {
            setMediaItems(songs.map { it.toMediaItem() })
            prepare()
            play()
        }
    }
}
```

## WorkManager para Descargas

### DownloadWorker

```kotlin
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MusicRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val songId = inputData.getString("songId") ?: return Result.failure()
        
        return try {
            // Descargar archivo
            val file = downloadAudioFile(songId)
            
            // Guardar en Room
            repository.insertSong(
                SongEntity(
                    id = songId,
                    path = file.absolutePath,
                    isDownloaded = true
                )
            )
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

## Testing Strategy

### Unit Tests
- ViewModels: Lógica de negocio
- Repositories: Transformación de datos
- Mappers: Conversiones DTO <-> Entity

### Integration Tests
- Room DAOs: Operaciones de base de datos
- API Services: Llamadas de red (MockWebServer)

### UI Tests
- Composables: Interacción de usuario
- Navegación: Flujos de pantallas

## Consideraciones de Rendimiento

1. **LazyColumn** para listas grandes (albums, canciones)
2. **Paging 3** podría implementarse para scroll infinito
3. **Coil** con caché de disco para imágenes
4. **Flow.conflate()** para evitar sobrecarga de actualizaciones
5. **WorkManager** con constraints para descargas eficientes
