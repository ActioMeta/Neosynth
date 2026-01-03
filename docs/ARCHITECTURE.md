# Arquitectura del Proyecto NeoSynth# Arquitectura del Proyecto



## Tabla de Contenidos## Visión General



1. [Visión General](#visión-general)NeoSynth sigue una arquitectura de tres capas basada en las recomendaciones de Android Architecture Components, con separación clara de responsabilidades y flujo de datos unidireccional.

2. [Arquitectura de Capas](#arquitectura-de-capas)

3. [Patrones de Diseño](#patrones-de-diseño)## Diagrama de Capas

4. [Componentes Principales](#componentes-principales)

5. [Flujo de Datos](#flujo-de-datos)```

6. [Gestión de Estado](#gestión-de-estado)┌─────────────────────────────────────────────────────────────┐

│                         UI LAYER                            │

## Visión General│  Jetpack Compose + Material 3 + Navigation                  │

│                                                             │

NeoSynth sigue una arquitectura **MVVM (Model-View-ViewModel)** con **Repository Pattern** y **Clean Architecture**, separando claramente las responsabilidades en capas independientes.│  - Composables                                              │

│  - Screens                                                  │

### Principios Arquitectónicos│  - Navigation Graph                                         │

│  - UI State                                                 │

- **Separación de Responsabilidades**: Cada capa tiene un propósito específico└────────────────────────────┬────────────────────────────────┘

- **Inyección de Dependencias**: Usando Hilt para gestión centralizada                             │

- **Programación Reactiva**: Flow y StateFlow para flujos de datos reactivos                             ▼

- **Single Source of Truth**: Room como fuente única de verdad para datos locales┌─────────────────────────────────────────────────────────────┐

- **Unidirectional Data Flow**: Los datos fluyen en una sola dirección│                      VIEWMODEL LAYER                        │

│  State Management + Business Logic                          │

## Arquitectura de Capas│                                                             │

│  - ViewModels                                               │

```│  - StateFlow / Flow                                         │

┌─────────────────────────────────────────┐│  - Coroutines                                               │

│          UI Layer (Compose)             ││  - Use Cases (opcional)                                     │

│  - Screens                              │└────────────────────────────┬────────────────────────────────┘

│  - Components                           │                             │

│  - Navigation                           │                             ▼

└──────────────┬──────────────────────────┘┌─────────────────────────────────────────────────────────────┐

               ││                       DATA LAYER                            │

┌──────────────▼──────────────────────────┐│  Room (local) + Retrofit (remote) + WorkManager             │

│         ViewModel Layer                 ││                                                             │

│  - ViewModels                           ││  - Repositories                                             │

│  - UI State                             ││  - Data Sources (Local/Remote)                              │

│  - UI Events                            ││  - DAOs                                                     │

└──────────────┬──────────────────────────┘│  - API Services                                             │

               ││  - Workers                                                  │

┌──────────────▼──────────────────────────┐└─────────────────────────────────────────────────────────────┘

│        Domain Layer                     │```

│  - Use Cases (opcional)                 │

│  - Business Logic                       │## Estructura de Carpetas

└──────────────┬──────────────────────────┘

               │```

┌──────────────▼──────────────────────────┐app/src/main/java/com/example/neosynth/

│         Data Layer                      ││

│  ┌────────────────────────────────────┐ │├── data/

│  │ Repositories                       │ ││   ├── local/

│  └──┬──────────────────────────┬──────┘ ││   │   ├── dao/

│     │                          │        ││   │   │   ├── MusicDao.kt              # Operaciones de canciones y playlists

│  ┌──▼────────┐          ┌──────▼─────┐ ││   │   │   └── ServerDao.kt             # Operaciones de servidores

│  │  Remote   │          │   Local    │ ││   │   ├── entities/

│  │ (Retrofit)│          │   (Room)   │ ││   │   │   ├── SongEntity.kt            # Tabla de canciones

│  └───────────┘          └────────────┘ ││   │   │   ├── PlaylistEntity.kt        # Tabla de playlists

└─────────────────────────────────────────┘│   │   │   ├── PlaylistSongCrossRef.kt  # Relación N:M

```│   │   │   ├── PlaylistWithSongs.kt     # DTO de relación

│   │   │   └── ServerEntity.kt          # Tabla de servidores

### 1. UI Layer (Presentation)│   │   └── MusicDatabase.kt             # Room Database

│   │

**Responsabilidades:**│   ├── remote/

- Renderizar la interfaz de usuario│   │   ├── api/

- Manejar interacciones del usuario│   │   │   └── NavidromeApi.kt          # Interface Retrofit

- Observar cambios de estado del ViewModel│   │   ├── dto/

│   │   │   ├── AlbumDto.kt              # Modelos de respuesta API

**Componentes:**│   │   │   ├── SongDto.kt

- `screens/`: Pantallas completas de la aplicación│   │   │   └── PlaylistDto.kt

- `components/`: Componentes reutilizables de UI│   │   └── mapper/

- `navigation/`: Sistema de navegación│   │       └── EntityMappers.kt         # DTO -> Domain

- `theme/`: Configuración de temas y estilos│   │

│   ├── repository/

**Tecnologías:**│   │   ├── MusicRepository.kt           # Coordinador de datos

- Jetpack Compose│   │   └── ServerRepository.kt          # Gestión de servidores

- Material Design 3│   │

- Navigation Compose│   └── worker/

│       └── DownloadWK.kt                # WorkManager para descargas

### 2. ViewModel Layer│

├── domain/

**Responsabilidades:**│   ├── model/

- Gestionar el estado de la UI│   │   ├── Song.kt                      # Modelo de dominio

- Procesar eventos de usuario│   │   ├── Album.kt

- Comunicarse con repositorios│   │   ├── Artist.kt

- Sobrevivir a cambios de configuración│   │   └── Playlist.kt

│   │

**Patrón de Estructura:**│   └── provider/

│       └── MusicProvider.kt             # Interface de proveedor

```kotlin│

class ExampleViewModel @Inject constructor(├── player/

    private val repository: ExampleRepository│   ├── MusicController.kt               # Controlador de reproducción

) : ViewModel() {│   └── PlaybackService.kt               # MediaSessionService

    │

    // Estado privado mutable├── ui/

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)│   ├── components/

    │   │   ├── AlbumCard.kt                 # Componentes reutilizables

    // Estado público inmutable│   │   ├── MiniPlayer.kt

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()│   │   └── StickyHeader.kt

    │   │

    // Funciones para manejar eventos│   ├── home/

    fun onEvent(event: ExampleEvent) {│   │   ├── HomeScreen.kt                # Pantalla principal

        viewModelScope.launch {│   │   └── HomeViewModel.kt             # Lógica de home

            // Lógica de negocio│   │

        }│   ├── player/

    }│   │   ├── PlayerScreen.kt              # Reproductor completo

}│   │   └── PlayerViewModel.kt

```│   │

│   ├── downloads/

### 3. Domain Layer (Opcional)│   │   ├── DownloadScreen.kt            # Gestión de descargas

│   │   └── DownloadViewModel.kt

**Responsabilidades:**│   │

- Lógica de negocio pura│   ├── album/

- Casos de uso específicos│   │   ├── AlbumDetailScreen.kt         # Detalle de álbum

- Validaciones y transformaciones│   │   └── AlbumDetailViewModel.kt

│   │

**Nota:** En NeoSynth, gran parte de la lógica de dominio está en ViewModels y Repositories por simplicidad.│   ├── playlist/

│   │   ├── PlaylistDetailScreen.kt      # Detalle de playlist

### 4. Data Layer│   │   └── PlaylistDetailViewModel.kt

│   │

**Responsabilidades:**│   ├── login/

- Gestión de fuentes de datos│   │   ├── LoginScreen.kt               # Autenticación

- Sincronización entre API y base de datos local│   │   └── LoginViewModel.kt

- Caché y persistencia│   │

│   ├── navigation/

**Componentes:**│   │   ├── NavGraph.kt                  # Grafo de navegación

│   │   └── Screen.kt                    # Definición de rutas

#### Repositories│   │

│   └── theme/

Actúan como Single Source of Truth, decidiendo si obtener datos de la API o de la base de datos local.│       ├── Color.kt                     # Paleta de colores M3

│       ├── Theme.kt                     # Composable de tema

```kotlin│       └── Type.kt                      # Tipografía M3

class MusicRepository @Inject constructor(│

    private val api: NavidromeApiService,├── depsInjection/

    private val dao: SongDao,│   ├── AppModule.kt                     # Módulo principal Hilt

    private val serverDao: ServerDao│   ├── DatabaseModule.kt                # Inyección de Room

) {│   ├── NetworkModule.kt                 # Inyección de Retrofit

    // Ejemplo de patrón Repository│   └── RepositoryModule.kt              # Inyección de repositorios

    suspend fun getSongs(): Flow<List<Song>> = flow {│

        // 1. Emitir datos locales inmediatamente├── receiver/

        emit(dao.getAllSongs())│   └── VoiceCommandReceiver.kt          # BroadcastReceiver Google Assistant

        │

        // 2. Obtener datos actualizados del servidor├── utils/

        try {│   ├── AuthUtils.kt                     # Utilidades de autenticación

            val server = serverDao.getActiveServer()│   ├── FileUtils.kt                     # Gestión de archivos

            val response = api.getSongs(...)│   └── PreferencesManager.kt            # SharedPreferences

            │

            // 3. Actualizar base de datos local└── MainActivity.kt                      # Punto de entrada

            dao.insertAll(response.songs)```

            

            // 4. Emitir datos actualizados## Flujo de Datos

            emit(dao.getAllSongs())

        } catch (e: Exception) {### 1. Flujo de Lectura (Streaming/Offline)

            // Manejar error

        }```

    }User Action (UI)

}      │

```      ▼

ViewModel observa State

#### Remote Data Source      │

      ▼

**Tecnología:** Retrofit + OkHttpRepository consulta

      │

**Características:**      ├─────────────┬─────────────┐

- Autenticación con tokens MD5      ▼             ▼             ▼

- Interceptores para logging  Local (Room)  Remote (API)  Cache

- Manejo de errores HTTP      │             │             │

- Serialización JSON con Gson      └─────────────┴─────────────┘

                    │

**Archivos principales:**                    ▼

- `NavidromeApiService.kt`: Definición de endpoints          StateFlow emite datos

- `NavidromeResponses.kt`: Modelos de respuesta                    │

- `RetrofitModule.kt`: Configuración de Retrofit                    ▼

            UI se recompone

#### Local Data Source```



**Tecnología:** Room Database### 2. Flujo de Escritura (Descarga/Favoritos)



**Características:**```

- Caché de datos del servidorUser Action (UI)

- Almacenamiento de descargas      │

- Gestión de playlists offline      ▼

- Sincronización de favoritosViewModel dispara acción

      │

**Esquema de Base de Datos:**      ▼

Repository ejecuta operación

```      │

┌──────────────┐     ┌──────────────┐     ┌──────────────┐      ├─────────────┬─────────────┐

│   servers    │     │    songs     │     │  playlists   │      ▼             ▼             ▼

├──────────────┤     ├──────────────┤     ├──────────────┤Guarda en Room  Envía a API  WorkManager

│ id (PK)      │     │ id (PK)      │     │ id (PK)      │      │             │             │

│ name         │     │ title        │     │ name         │      └─────────────┴─────────────┘

│ url          │     │ artist       │     │ songCount    │                    │

│ username     │     │ album        │     │ duration     │                    ▼

│ token        │     │ duration     │     │ coverArt     │       Actualiza StateFlow

│ salt         │     │ coverArt     │     │ created      │                    │

│ isActive     │     │ path         │     │ changed      │                    ▼

└──────────────┘     │ isDownloaded │     └──────────────┘           UI se actualiza

                     │ isFavorite   │```

                     └──────────────┘

                            │## Patrones Utilizados

                            │ N:M

                            ▼### Repository Pattern

              ┌──────────────────────────┐Abstrae la fuente de datos (local o remota) del resto de la aplicación.

              │ playlist_song_cross_ref  │

              ├──────────────────────────┤```kotlin

              │ playlistId (FK)          │class MusicRepository @Inject constructor(

              │ songId (FK)              │    private val musicDao: MusicDao,

              │ position                 │    private val navidromeApi: NavidromeApi

              └──────────────────────────┘) {

```    fun getDownloadedSongs(): Flow<List<SongEntity>> {

        return musicDao.getDownloadedSongs()

**DAOs Principales:**    }

- `ServerDao`: Gestión de servidores    

- `SongDao`: Operaciones sobre canciones    suspend fun searchSongs(query: String): List<Song> {

- `PlaylistDao`: Gestión de playlists        return navidromeApi.search(query).toSongs()

- `FavoriteDao`: Gestión de favoritos    }

}

## Patrones de Diseño```



### 1. Repository Pattern### MVVM (Model-View-ViewModel)

Separa la lógica de UI de la lógica de negocio.

**Propósito:** Abstraer la fuente de datos y proporcionar una API limpia para acceder a ellos.

```kotlin

**Implementación:**@HiltViewModel

class HomeViewModel @Inject constructor(

```kotlin    private val repository: MusicRepository

interface MusicRepository {) : ViewModel() {

    suspend fun getAlbums(): List<Album>    private val _albums = MutableStateFlow<List<Album>>(emptyList())

    suspend fun downloadSong(song: Song)    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    fun getDownloadedSongs(): Flow<List<Song>>    

}    fun loadAlbums() {

        viewModelScope.launch {

class MusicRepositoryImpl @Inject constructor(            _albums.value = repository.getRecentAlbums()

    private val api: NavidromeApiService,        }

    private val dao: SongDao    }

) : MusicRepository {}

    // Implementación```

}

```### Dependency Injection (Hilt)

Proporciona dependencias automáticamente sin acoplamiento.

### 2. Dependency Injection (Hilt)

```kotlin

**Propósito:** Gestión centralizada de dependencias y ciclo de vida.@Module

@InstallIn(SingletonComponent::class)

**Módulos:**object DatabaseModule {

    @Provides

- `DatabaseModule`: Provee Room Database y DAOs    @Singleton

- `NetworkModule`: Provee Retrofit y ApiService    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase {

- `RepositoryModule`: Provee Repositories        return Room.databaseBuilder(

- `PlayerModule`: Provee MediaController            context,

            MusicDatabase::class.java,

**Scopes:**            "neosynth_db"

- `@Singleton`: Instancia única en toda la app        ).build()

- `@ViewModelScoped`: Instancia única por ViewModel    }

- `@ActivityRetainedScoped`: Sobrevive a cambios de configuración}

```

### 3. Observer Pattern (Flow/StateFlow)

### Single Source of Truth (SSOT)

**Propósito:** Observar cambios de datos de forma reactiva.Room es la única fuente de verdad para datos locales.



**Tipos de Flow:**```kotlin

suspend fun syncPlaylist(playlistId: String) {

- `Flow<T>`: Stream frío, se activa al recolectar    // 1. Obtener de API

- `StateFlow<T>`: Stream caliente con estado actual    val playlistDto = api.getPlaylist(playlistId)

- `SharedFlow<T>`: Stream caliente sin estado inicial    

    // 2. Guardar en Room (SSOT)

**Ejemplo:**    dao.insertPlaylist(playlistDto.toEntity())

    

```kotlin    // 3. UI observa Flow desde Room automáticamente

// En ViewModel}

private val _songs = MutableStateFlow<List<Song>>(emptyList())```

val songs: StateFlow<List<Song>> = _songs.asStateFlow()

## Inyección de Dependencias

// En Composable

val songs by viewModel.songs.collectAsState()### Módulos Hilt

```

**AppModule.kt** - Contexto y SharedPreferences

### 4. State Hoisting```kotlin

@Module

**Propósito:** Mover el estado al nivel más alto necesario para compartirlo.@InstallIn(SingletonComponent::class)

object AppModule {

```kotlin    @Provides

@Composable    @Singleton

fun ParentScreen() {    fun provideContext(@ApplicationContext context: Context) = context

    var searchQuery by remember { mutableStateOf("") }}

    ```

    SearchBar(

        query = searchQuery,**DatabaseModule.kt** - Room Database y DAOs

        onQueryChange = { searchQuery = it }```kotlin

    )@Module

    @InstallIn(SingletonComponent::class)

    SongList(filter = searchQuery)object DatabaseModule {

}    @Provides

```    @Singleton

    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase

### 5. Unidirectional Data Flow (UDF)    

    @Provides

**Propósito:** Los datos fluyen en una sola dirección, los eventos en la dirección opuesta.    fun provideMusicDao(db: MusicDatabase): MusicDao = db.musicDao()

}

``````

┌─────────────┐

│     UI      │ ──events──> ┌──────────────┐**NetworkModule.kt** - Retrofit y OkHttp

│  (Compose)  │             │  ViewModel   │```kotlin

│             │ <──state─── │              │@Module

└─────────────┘             └──────┬───────┘@InstallIn(SingletonComponent::class)

                                   │object NetworkModule {

                            ┌──────▼───────┐    @Provides

                            │  Repository  │    @Singleton

                            └──────────────┘    fun provideOkHttpClient(): OkHttpClient

```    

    @Provides

## Componentes Principales    @Singleton

    fun provideRetrofit(client: OkHttpClient): Retrofit

### 1. Player Service    

    @Provides

**Archivo:** `PlayerService.kt`    @Singleton

    fun provideNavidromeApi(retrofit: Retrofit): NavidromeApi

**Responsabilidades:**}

- Reproducción de audio en segundo plano```

- Control mediante notificaciones

- Gestión de cola de reproducción## Estado de UI

- Persistencia entre sesiones

### StateFlow para Estado Reactivo

**Tecnología:** Media3 (ExoPlayer)

```kotlin

**Características:**@HiltViewModel

- MediaSessionService para compatibilidad con Android Autoclass DownloadViewModel @Inject constructor(

- Notificaciones con controles de reproducción    private val repository: MusicRepository

- Soporte para comandos de voz (Google Assistant)) : ViewModel() {

- Manejo de audio focus    // Estado inmutable expuesto

- Caché de streaming    private val _downloadedSongs = MutableStateFlow<List<SongEntity>>(emptyList())

    val downloadedSongs: StateFlow<List<SongEntity>> = _downloadedSongs.asStateFlow()

### 2. Download System    

    // Estado de UI

**Archivos:**    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

- `DownloadWorker.kt`: Worker para descargas en segundo plano    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

- `DownloadViewModel.kt`: Gestión del estado de descargas    

    init {

**Estrategia de Descarga Híbrida:**        viewModelScope.launch {

            repository.getDownloadedSongs()

```kotlin                .collect { songs ->

// Batch processing con WorkContinuation                    _downloadedSongs.value = songs

fun downloadPlaylist(songs: List<Song>) {                    _uiState.value = UiState.Success

    val batchSize = 10                }

    val batches = songs.chunked(batchSize)        }

        }

    var continuation: WorkContinuation? = null}

    ```

    batches.forEach { batch ->

        // Crear trabajos paralelos para el batch### Sealed Class para Estados

        val parallelWorks = batch.map { song ->

            createDownloadWork(song)```kotlin

        }sealed class UiState {

            object Loading : UiState()

        continuation = if (continuation == null) {    object Success : UiState()

            workManager.beginWith(parallelWorks)    data class Error(val message: String) : UiState()

        } else {}

            continuation!!.then(parallelWorks)```

        }

    }## Navegación

    

    continuation?.enqueue()### Rutas Tipadas

}

``````kotlin

sealed class Screen(val route: String) {

**Características:**    object Home : Screen("home")

- Descarga en batches de 10 canciones en paralelo    object Player : Screen("player")

- Batches secuenciales para evitar sobrecarga    object Downloads : Screen("downloads")

- Contador atómico para progreso en notificaciones    data class AlbumDetail(val albumId: String) : Screen("album/{albumId}") {

- Reintentos automáticos en caso de fallo        fun createRoute(albumId: String) = "album/$albumId"

- Persistencia de estado con WorkManager    }

}

### 3. Navigation System```



**Archivo:** `NeosynthNavGraph.kt`### NavGraph



**Estructura:**```kotlin

@Composable

```kotlinfun NavGraph(

sealed class Screen(val route: String) {    navController: NavHostController,

    object Home : Screen("home")    musicController: MusicController

    object Albums : Screen("albums")) {

    data class AlbumDetail(val albumId: String) : Screen("album/{albumId}")    NavHost(navController, startDestination = Screen.Home.route) {

    // ...        composable(Screen.Home.route) {

}            HomeScreen(navController, musicController)

```        }

        composable(

**Características:**            route = Screen.AlbumDetail().route,

- Type-safe navigation            arguments = listOf(navArgument("albumId") { type = NavType.StringType })

- Paso de argumentos entre pantallas        ) { backStackEntry ->

- Deep linking support            val albumId = backStackEntry.arguments?.getString("albumId")

- Back stack management            AlbumDetailScreen(albumId, navController)

        }

### 4. Offline Support    }

}

**Componentes:**```



1. **Download Manager:**## Gestión de Reproducción

   - Descarga de archivos de audio

   - Almacenamiento en almacenamiento interno### Media3 ExoPlayer

   - Metadatos en Room Database

```kotlin

2. **Playlist Offline:**@AndroidEntryPoint

   - Filtrado de playlists con al menos 1 canción descargadaclass PlaybackService : MediaSessionService() {

   - Visualización de todas las canciones (descargadas y pendientes)    private lateinit var player: ExoPlayer

   - Indicadores visuales de estado de descarga    private lateinit var mediaSession: MediaSession

    

3. **Sync Manager:**    override fun onCreate() {

   - Sincronización de favoritos con servidor        super.onCreate()

   - Batch operations para reducir llamadas API        player = ExoPlayer.Builder(this).build()

   - Manejo de conflictos        mediaSession = MediaSession.Builder(this, player).build()

    }

## Flujo de Datos    

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

### Caso de Uso: Reproducir un Álbum}

```

```

1. Usuario hace clic en "Play" en AlbumDetailScreen### MusicController

   │

   ▼```kotlin

2. AlbumDetailScreen llama a viewModel.playAlbum()class MusicController @Inject constructor(

   │    private val context: Context

   ▼) {

3. AlbumDetailViewModel obtiene canciones del álbum    private val sessionToken = SessionToken(

   │        context,

   ▼        ComponentName(context, PlaybackService::class.java)

4. ViewModel envía lista de canciones a PlayerService    )

   │    

   ▼    private val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

5. PlayerService prepara MediaItems y comienza reproducción    

   │    fun playQueue(songs: List<SongEntity>) {

   ▼        controllerFuture.get()?.apply {

6. PlayerService emite estado actualizado (isPlaying, currentSong)            setMediaItems(songs.map { it.toMediaItem() })

   │            prepare()

   ▼            play()

7. UI observa cambios y actualiza interfaz        }

```    }

}

### Caso de Uso: Descargar Playlist```



```## WorkManager para Descargas

1. Usuario selecciona "Descargar" en PlaylistDetailScreen

   │### DownloadWorker

   ▼

2. ViewModel crea batch de DownloadWorkers```kotlin

   │@HiltWorker

   ▼class DownloadWorker @AssistedInject constructor(

3. WorkManager encola trabajos en batches    @Assisted context: Context,

   │    @Assisted params: WorkerParameters,

   ▼    private val repository: MusicRepository

4. DownloadWorker descarga archivo de audio) : CoroutineWorker(context, params) {

   │    

   ▼    override suspend fun doWork(): Result {

5. Worker actualiza Room Database (isDownloaded = true)        val songId = inputData.getString("songId") ?: return Result.failure()

   │        

   ▼        return try {

6. DownloadProgress notifica progreso            // Descargar archivo

   │            val file = downloadAudioFile(songId)

   ▼            

7. UI observa cambios en Flow y actualiza indicadores            // Guardar en Room

```            repository.insertSong(

                SongEntity(

## Gestión de Estado                    id = songId,

                    path = file.absolutePath,

### Patrón de Estado en ViewModels                    isDownloaded = true

                )

```kotlin            )

sealed class UiState {            

    object Loading : UiState()            Result.success()

    data class Success(val data: List<Song>) : UiState()        } catch (e: Exception) {

    data class Error(val message: String) : UiState()            Result.retry()

}        }

    }

class MusicViewModel @Inject constructor(}

    private val repository: MusicRepository```

) : ViewModel() {

    ## Testing Strategy

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()### Unit Tests

    - ViewModels: Lógica de negocio

    init {- Repositories: Transformación de datos

        loadSongs()- Mappers: Conversiones DTO <-> Entity

    }

    ### Integration Tests

    private fun loadSongs() {- Room DAOs: Operaciones de base de datos

        viewModelScope.launch {- API Services: Llamadas de red (MockWebServer)

            _uiState.value = UiState.Loading

            try {### UI Tests

                repository.getSongs().collect { songs ->- Composables: Interacción de usuario

                    _uiState.value = UiState.Success(songs)- Navegación: Flujos de pantallas

                }

            } catch (e: Exception) {## Consideraciones de Rendimiento

                _uiState.value = UiState.Error(e.message ?: "Unknown error")

            }1. **LazyColumn** para listas grandes (albums, canciones)

        }2. **Paging 3** podría implementarse para scroll infinito

    }3. **Coil** con caché de disco para imágenes

}4. **Flow.conflate()** para evitar sobrecarga de actualizaciones

```5. **WorkManager** con constraints para descargas eficientes


### Estado en Composables

```kotlin
@Composable
fun MusicScreen(viewModel: MusicViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    
    when (val state = uiState) {
        is UiState.Loading -> LoadingIndicator()
        is UiState.Success -> SongList(state.data)
        is UiState.Error -> ErrorScreen(state.message)
    }
}
```

### Estado Local vs Estado de ViewModel

**Estado Local (remember):**
- Estado de UI transitorio (expandir/colapsar, tabs seleccionadas)
- No sobrevive a cambios de configuración
- No necesita ser compartido

```kotlin
var isExpanded by remember { mutableStateOf(false) }
```

**Estado de ViewModel (StateFlow):**
- Estado de negocio persistente
- Sobrevive a cambios de configuración
- Necesita ser compartido entre composables

```kotlin
val songs by viewModel.songs.collectAsState()
```

## Optimizaciones de Rendimiento

### 1. Lazy Loading

- LazyColumn para listas grandes
- Paginación en llamadas API
- Caché de imágenes con Coil

### 2. Coroutines

- Dispatchers.IO para operaciones de red y base de datos
- Dispatchers.Main para actualizaciones de UI
- Dispatchers.Default para cálculos intensivos

### 3. Room Database

- Indices en columnas frecuentemente consultadas
- Foreign keys para integridad referencial
- Transacciones para operaciones múltiples

### 4. Memory Management

- ViewModelScope para cancelación automática
- collectAsStateWithLifecycle para optimizar recolección
- remember y derivedStateOf para evitar recomposiciones innecesarias

## Testing

### Estructura de Tests

```
test/
├── viewmodel/      # Unit tests de ViewModels
├── repository/     # Unit tests de Repositories
├── dao/           # Unit tests de DAOs
└── utils/         # Unit tests de utilidades

androidTest/
├── ui/            # UI tests con Compose
├── database/      # Tests de integración con Room
└── worker/        # Tests de Workers
```

### Estrategia de Testing

1. **Unit Tests:** ViewModels y Repositories (mocks)
2. **Integration Tests:** Room Database (in-memory)
3. **UI Tests:** Pantallas críticas con Compose Test
4. **End-to-End:** Flujos principales de usuario

## Consideraciones de Seguridad

1. **Autenticación:**
   - Tokens almacenados en Room Database (encriptado en producción)
   - MD5 salt para autenticación Subsonic
   - No se almacenan contraseñas en texto plano

2. **Network:**
   - HTTPS obligatorio para servidores
   - Certificate pinning (opcional)
   - Validación de respuestas del servidor

3. **Storage:**
   - Archivos descargados en almacenamiento interno
   - Base de datos protegida por permisos de Android
   - Limpieza de caché al cerrar sesión
