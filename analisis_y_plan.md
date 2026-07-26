# Análisis y Plan de Acción: Mejoras, Rediseños y Correcciones en NeoSynth Android

Este documento presenta un diagnóstico exhaustivo de la situación actual del proyecto **NeoSynth**, junto con el análisis de los nuevos requerimientos de rediseño para las pantallas de estadísticas, descargas, selección múltiple en descargas, cola y discover, gestión de favoritas, además del detalle del estilo de movimiento y rebote **Material You Expressive** y las correcciones de bugs.

---

## 1. Diagnóstico del Estado de la Aplicación

La arquitectura general del reproductor de música está bien estructurada utilizando **Jetpack Compose** para la interfaz de usuario, **Hilt** para la inyección de dependencias y **Media3 (ExoPlayer)** para la gestión de reproducción en segundo plano. La sincronización se maneja mediante un `MusicController` centralizado que actúa como puente entre la UI y el `MediaBrowser`/`MediaController` de Media3.

Sin embargo, se requieren cambios estéticos y de usabilidad para mejorar la navegación, el control de las selecciones masivas, el ordenamiento de los elementos descargados y el acceso a la música favorita.

---

## 2. Rediseño del Control de Selección y Acciones Masivas (Descargas, Cola y Discover)

### Requerimiento
* Eliminar la barra de selección lateral flotante (`SideMultiSelectBar`) en **Descargas** y **Discover** (Búsquedas, Géneros y Décadas), así como el grupo de botones flotantes (`FabGroup`) en la **Cola de reproducción**.
* Reemplazarlos por una barra de menú de selección inferior (**Bottom Selection Bar**) de **3 botones** que se superponga justo por encima de la barra de navegación (o MiniPlayer), activa únicamente cuando hay elementos seleccionados.

### Diseño de la Barra de Selección (3 Botones)
```
+-----------------------------------------------------------+
| [X] Quitar Selecciones  | (>) Reproducir |  (ooo) Menú    |
+-----------------------------------------------------------+
```
1. **Botón 1 (Quitar Selecciones)**: Cancela el modo de selección y desmarca todas las canciones (`selectedSongIds = emptySet()`).
2. **Botón 2 (Reproducir Seleccionadas)**: Inicia la reproducción inmediata de los elementos seleccionados en el orden preciso de selección.
3. **Botón 3 (Menú de Opciones)**: Abre un `ModalBottomSheet` (menú contextual inferior) con las opciones restantes.

#### Acciones del Menú (Bottom Sheet) según Pantalla:
* **En Descargas (`DownloadScreen`)**:
  * *Añadir a Favoritos*: Marca los elementos como favoritos en la BD.
  * *Añadir a Playlist*: Despliega el diálogo para asociar los elementos a una lista.
  * *Añadir a la Cola*: Inserta las canciones seleccionadas al final de la cola activa.
  * *Eliminar Descarga*: Borra físicamente el archivo y su registro de descarga.
* **En la Cola (`QueueBottomSheet`)**:
  * *Guardar como Playlist*: Guarda la cola actual como una nueva playlist en la biblioteca.
  * *Descargar Cola*: Inicia la descarga masiva de todos los elementos de la cola.
  * *Limpiar Cola*: Elimina todas las canciones de la cola activa.
* **En Discover (`DiscoverScreen`)** (Búsquedas, Géneros y Décadas):
  * *Añadir a Favoritos*: Marca las canciones en la base de datos local.
  * *Descargar Selección*: Encola la descarga de las canciones.
  * *Añadir a Playlist*: Despliega el diálogo de playlists.

### Análisis Técnico
* **Selección múltiple en la Cola**: Se añadirá un estado de selección (`selectedQueueIds: Set<String>`) y un modo de selección activado por pulsación larga o checkbox en cada `QueueItem`.
* **Ubicación de la Barra**: El componente se pintará en el Scaffold/Box principal de `DownloadScreen`, `DiscoverScreen` y `QueueBottomSheet`, ajustando su padding inferior para situarse justo encima de la barra de navegación del sistema y el reproductor en miniatura.

---

## 3. Rediseño de la Pantalla de Estadísticas (`StatsScreen`)

### Requerimiento
* Eliminar el layout actual y quitar la tarjeta principal (`WeeklyMinutesCard` / Tiempo de Escucha).
* Añadir un carrusel horizontal de opciones de periodo en la parte superior: **Hoy, Semana, Mes, Año, Todo**.
* Mostrar una lista vertical de Tops clasificados por categorías: **Top Canciones, Top Artistas, Top Géneros**.
* Permitir hacer clic en las categorías (o en un botón "Ver detalles") para acceder a una vista detallada (Top 10/20) del periodo seleccionado.

### Mockup del Layout
```
+-----------------------------------------------------------+
|                        ESTADÍSTICAS                       |
|                                                           |
|  ( Hoy )  [ Semana ]  ( Mes )  ( Año )  ( Todo )          |  <-- Carrusel
|                                                           |
|  TOP CANCIONES                                            |
|  1. Song Title - Artist (120 min)                         |
|  2. Another Song - Artist (95 min)                        |
|  [ Ver detalles > ]                                       |
|                                                           |
|  TOP ARTISTAS                                             |
|  1. Artist Name (340 min)                                 |
|  2. Another Artist (280 min)                              |
|  [ Ver detalles > ]                                       |
|                                                           |
|  TOP GÉNEROS                                              |
|  1. Pop (450 min)                                         |
|  2. Rock (320 min)                                        |
|  [ Ver detalles > ]                                       |
+-----------------------------------------------------------+
```

### Análisis Técnico
1. **Base de Datos y DAO (`PlaybackHistoryDao.kt`)**:
   * Las consultas de artistas (`getTopArtistsWithTime`) y géneros (`getTopGenresWithTime`) ya admiten un parámetro `sinceTimestamp`.
   * Se requiere añadir una nueva consulta para canciones:
     ```kotlin
     @Query("""
         SELECT songId, title, artist, SUM(durationListened) as totalTimeMs, COUNT(*) as playCount 
         FROM playback_history 
         WHERE timestamp >= :sinceTimestamp 
         GROUP BY songId 
         ORDER BY totalTimeMs DESC 
         LIMIT :limit
     """)
     fun getTopSongsWithTime(sinceTimestamp: Long, limit: Int): Flow<List<SongTimeCount>>
     ```
2. **ViewModel (`StatsViewModel.kt`)**:
   * Reemplazar los filtros separados por un único `MutableStateFlow(TimeFilter.WEEK)` compartido para todas las categorías.
   * Ampliar `TimeFilter` para incluir `WEEK` y `ALL`:
     ```kotlin
     enum class TimeFilter { DAY, WEEK, MONTH, YEAR, ALL }
     ```
   * Modificar `getSinceTimestamp(filter)` para soportar:
     * `WEEK`: Inicio del lunes de la semana corriente.
     * `ALL`: Retornar `0L` (toda la historia).
   * Crear flujos reactivos expuestos a la UI para los Tops de canciones, artistas y géneros basados en este filtro temporal común.
3. **UI (`StatsScreen.kt`)**:
   * Reemplazar `LazyColumn` para pintar los tops en un formato limpio de listas con avatares/portadas de tamaño compacto.
   * Implementar `Top10Dialog` o un menú modal bottom sheet extendido para mostrar la lista completa al pulsar "Ver detalles" de cada categoría.

---

## 4. Rediseño de la Pantalla de Descargas (`DownloadScreen`)

### Requerimiento
* Cambiar la distribución de la pantalla:
  1. Título superior: "Descargas" / "Download" según el idioma del dispositivo.
  2. Botón desplegable (`DropdownMenu`) junto al título para ordenar la lista por: **Ascendente, Descendente, Por título, Por artista, Por álbum, Recientes**.
  3. Carrusel horizontal de opciones (Chips): **Canciones, Álbumes, Artistas, Listas de reproducción, Favoritos**.

### Mockup del Layout
```
+-----------------------------------------------------------+
| Descargas  [ Ordenar por v ]                              |  <-- Título + Dropdown
|                                                           |
| [Canciones] (Álbumes) (Artistas) (Playlists) (Favoritos)  |  <-- Carrusel horizontal
|                                                           |
| --------------------------------------------------------- |
|   Lista de elementos filtrados y ordenados...             |
+-----------------------------------------------------------+
```

### Análisis Técnico
1. **Gestión del Ordenamiento (`DownloadViewModel.kt`)**:
   * Crear un enum `SortType` con los valores: `ASCENDING`, `DESCENDING`, `TITLE`, `ARTIST`, `ALBUM`, `RECENT`.
   * Mantener el estado de ordenamiento en el ViewModel mediante un `MutableStateFlow`.
2. **Filtros del Carrusel (`DownloadScreen.kt`)**:
   * Modificar `FilterType` o utilizar un estado dedicado para mapear las 5 opciones del carrusel:
     * *Canciones*: Muestra todas las pistas individuales descargadas.
     * *Álbumes*: Agrupa y muestra álbumes que tienen canciones descargadas.
     * *Artistas*: Agrupa y muestra artistas de las canciones descargadas.
     * *Listas de reproducción*: Muestra las playlists locales/descargadas.
     * *Favoritos*: Muestra las canciones descargadas marcadas como favoritas (`isFavorite = true`).
3. **Lógica de Combinación**:
   * En el ViewModel o en la UI mediante `remember(groupedSongs, currentFilter, sortType)`, se filtrarán los elementos según la categoría seleccionada en el carrusel y luego se ordenarán según el criterio del botón desplegable antes de enviarse al renderizador de la lista.

---

## 5. Mejora en la Gestión de Favoritas

### Requerimiento
* Facilitar el acceso de las canciones favoritas desde distintas pantallas de la aplicación, ya que actualmente no existe un acceso directo intuitivo.

### Plan de Implementación
1. **Acceso desde Descargas (`DownloadScreen`)**:
   * El chip "Favoritos" en el carrusel de la pantalla de descargas filtrará y mostrará instantáneamente las canciones marcadas como favoritas que se encuentren guardadas de forma local.
2. **Pestaña de Biblioteca (`LibraryScreen.kt`)**:
   * Agregar una pestaña dedicada a "Favoritas" (además de Playlists, Álbumes y Artistas) o bien una fila destacada al principio de la pestaña "Playlists" con el título "Canciones Favoritas" (representada con un icono de corazón).
   * Al pulsarla, cargará y reproducirá el flujo completo de canciones marcadas con `isFavorite = true` en la BD (incluyendo canciones locales y de servidor si se está en modo online).
   * La inyección de este flujo se alimentará directamente de `IMusicRepository.getFavoriteSongs()`.

---

## 6. Animaciones y Movimiento: Material You Expressive

### Enfoque de Diseño
Se enfatizará la sensación de rebote y movimiento natural ("Overshoot") bajo las directrices de **Material You Expressive Motion**, asegurando que toda interacción se sienta fluida, viva y de gama alta.

### Puntos Clave de Implementación
1. **Micro-interacciones en Botones (Efecto "Push-Back")**:
   * Los botones de selección, chips de carrusel e IconButtons usarán una especificación de escala interactiva ante la presión del usuario (pulsación).
   * Modificador de escala con resorte: al presionar, el botón se encogerá levemente (ej: `0.94f`) y al soltar, rebotará más allá de su escala original (`1.05f`) antes de estabilizarse en `1.0f`:
     ```kotlin
     spring(
         dampingRatio = Spring.DampingRatioMediumBouncy,
         stiffness = Spring.StiffnessMediumLow
     )
     ```
2. **Entrada Flotante de la Barra de Selección de 3 Botones**:
   * Aparecerá desde abajo usando un desplazamiento vertical (`slideInVertically`) y escala inicial (`scaleIn`) combinadas. El interpolador de física del resorte usará baja amortiguación para lograr un rebote elástico distintivo al fijarse en su posición.
3. **Carga y Transición de Listas**:
   * Las transiciones de ordenación y filtrado de listas en Descargas y Discover se animarán dinámicamente con `Modifier.animateItemPlacement()` personalizado para que las canciones se deslicen y reboten a sus nuevos índices suavemente.
4. **Hojas de Diálogo e Interfaces Modales**:
   * El `ModalBottomSheet` de acciones secundarias de selección y los diálogos de detalle en Estadísticas usarán efectos de resorte elásticos al abrirse y cerrarse, reaccionando de manera natural a los gestos rápidos del usuario.

---

## 7. Plan de Solución para los Bugs Existentes

### Bug 1: Reinicio de la Reproducción al Reordenar la Cola
* **Causa**: Al reordenar la cola, ExoPlayer desplaza el índice actual (`currentIndex`). El `HorizontalPager` detecta que su página asentada no coincide con el nuevo índice de ExoPlayer y, creyendo que el usuario deslizó el carrusel de carátulas manualmente, llama a `seekTo(index, 0)`, reiniciando la canción.
* **Solución**: Añadir una bandera `userSwiped = remember { mutableStateOf(false) }` en `Player.kt`. Esta bandera se activará en `true` en el `LaunchedEffect` del arrastre (`isDragged`) del Pager de carátulas. Solo si `userSwiped` es `true` se enviará la orden de reproducción al cambiar de página, previniendo reinicios accidentales cuando ExoPlayer actualice el índice por movimientos programáticos en la cola.

### Bug 2: Adición de Canciones en Orden de Selección Original
* **Causa**: El código actual filtra las canciones seleccionadas comparando los IDs contra la lista de canciones original usando `.filter { it.id in selectedSongIds }`. Esto descarta el orden cronológico en el que el usuario seleccionó cada canción y vuelve a ordenarlas según la lista original.
* **Solución**: Iterar directamente sobre el conjunto ordenado de identificadores seleccionados (`selectedSongIds` mantiene el orden de inserción al ser un `LinkedHashSet`). Se mapeará cada ID a su objeto correspondiente usando un mapa asociativo temporal para evitar búsquedas cuadráticas $O(N^2)$:
  ```kotlin
  val songsMap = allSongs.associateBy { it.id }
  val orderedSelectedSongs = selectedSongIds.mapNotNull { id -> songsMap[id] }
  ```
  Este cambio se aplicará en los controladores y ViewModels de Descargas, Álbumes, Playlists, Búsqueda y Canciones Recientes.

---

## 8. Modificaciones al Home Screen y Carrusel de Álbumes

### 8.1. Corrección del Carrusel en Home
* **Deduplicación y Agrupación**: Modificar `HomeViewModel.loadOfflineData()` para filtrar duplicados usando `.distinctBy { it.albumID.ifEmpty { it.id } }`.
* **Corregir el Nombre del Álbum**: Mapear `name = song.album.ifEmpty { song.title }` para mostrar el nombre del álbum y no la canción.
* **Navegación al presionar**: Cambiar el callback `onClick` de la tarjeta del álbum en `HomeScreen.kt` para navegar directamente a `AlbumDetailScreen` (`onNavigateToAlbum(album.id)`) en lugar de reproducir.

### 8.2. Remoción del Botón Superior de Estadísticas e Integración en Lista Home
* **Requerimiento**: Eliminar el botón flotante / icono de ecualizador de estadísticas (`Icons.Rounded.Equalizer`) de la barra de acciones superior de `HomeScreen.kt`.
* **Nuevo Componente "Top 5 Semanal"**:
  * Agregar un nuevo bloque debajo del carrusel "Recently Added" que muestre el **Top 5 de canciones más escuchadas esta semana**.
  * Al pie de esta mini-lista del Top 5, añadir un botón llamativo (estilo Material You con gradiente o contorno) para acceder a la pantalla completa de estadísticas (`onNavigateToStats`).
  * Esto integra de forma más natural el acceso a las estadísticas dentro del feed principal del usuario.

### 8.3. Mejoras para la Vista de Detalles de Álbum (`AlbumDetailScreen`)
* **Palette API**: Fondo de cabecera con degradado dinámico según la portada.
* **Progreso de Descargas**: Indicador de qué porcentaje del álbum está en memoria local.
* **Búsqueda Interna**: Filtro rápido de pistas para álbumes recopilatorios.
* **Widget de Artista**: Fila inferior con más álbumes del mismo autor.

---

## 9. Mejoras en el Reproductor (Player) y Flujo de Letras (Lyrics)

### 9.1. Gesto en Carátula del Reproductor
* **Remover Double-Tap Skip**: Desactivar el detector de doble toque (`onDoubleTap`) sobre el cover art en `Player.kt` que desplaza el tiempo 10 segundos hacia adelante o atrás. Con esto evitamos saltos no deseados de pista.

### 9.2. Flujo de Letras Sincronizadas
* **Visualización Dinámica (Karaoke Style)**: Panel de letras con desplazamiento automático guiado por la pista en tiempo real.
* **Seek por Línea**: Pulsar un verso para hacer seek instantáneo a ese punto.
* **Difuminado Artístico**: Fondo de la letra con blur translúcido basado en la carátula activa.
* **Visualizador de Ondas**: Ondas de audio animadas reactivas al ritmo de la música.

---

## 10. Propuestas de Rediseño y UX para la Biblioteca (Library)

Para potenciar la visualización y experiencia del usuario en `LibraryScreen.kt`, implementaremos las siguientes mejoras:

### 10.1. Cambios Visuales Premium
* **Collage de Carátulas para Playlists**: En lugar de mostrar un icono predeterminado o una sola carátula, se generará una imagen dinámica tipo collage cuadrícula (2x2) combinando el arte de las primeras 4 canciones añadidas a la playlist.
* **Sticky Headers de Cristal Esmerilado (Glassmorphism)**: Los encabezados de letras del abecedario (`stickyHeader`) tendrán un efecto de desenfoque translúcido (`backdrop-filter`) de manera que las canciones se deslicen de forma visible y fluida por detrás al hacer scroll.
* **Segmentación con Color de Acento Dinámico**: Cada botón del segmented selector superior (Playlists, Álbumes, Artistas, Favoritas) tendrá un color de acento diferenciado al seleccionarse, rompiendo la monotonía del color de sistema.

### 10.2. Mejoras de Experiencia de Usuario (UX)
* **Gestos Rápidos de Deslizamiento (Quick Swipe Actions)**:
  * Deslizar a la **derecha** sobre un elemento (playlist, álbum, artista) para mostrar un botón de acción rápida de *Reproducción Inmediata* o *Añadir a Cola*.
  * Deslizar a la **izquierda** para revelar opciones de *Eliminar*, *Cambiar nombre* o *Anclar*.
* **Anclado de Elementos (Pinning)**:
  * Permitir que el usuario marque como "Anclado" sus playlists o álbumes predilectos.
  * Estos elementos se mantendrán fijos al inicio de la lista correspondiente, separados del resto de la ordenación alfabética, para un acceso inmediato.
* **Interruptor Grid / List View**:
  * Un botón discreto en la barra superior para alternar la visualización entre un listado compacto o una cuadrícula de tarjetas visuales (especialmente disfrutable en la pestaña de Álbumes y Artistas).

---

## 11. Modificaciones en Otras Áreas

### 11.1. MiniPlayer con Gestos
* **Deslizar para Expandir**: Gesto swipe up sobre el MiniPlayer para elevar el reproductor completo.
* **Swipe lateral**: Gesto a la derecha/izquierda para pasar a la canción previa o siguiente.

### 11.2. Gráficas Estadísticas Comparativas
* En la pantalla de estadísticas (`StatsScreen`), agregar gráficos de barras o áreas reactivos y con el estilo de color del sistema para comparar visualmente el rendimiento de escucha de la semana actual frente a la anterior.
