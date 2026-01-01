# Material Design 3 Implementation

## Overview

NeoSynth implements Material Design 3 (Material You) completely, providing a modern, adaptive, and accessible user interface that follows Google's latest design guidelines.

## Dynamic Color (Material You)

### Implementation

```kotlin
@Composable
fun NeoSynth_androidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic colors available on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Fallback to static colors
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

### Color Tokens

**Primary Colors**
```kotlin
MaterialTheme.colorScheme.primary           // Main brand color
MaterialTheme.colorScheme.onPrimary         // Text on primary
MaterialTheme.colorScheme.primaryContainer  // Emphasized containers
MaterialTheme.colorScheme.onPrimaryContainer // Text on primary container
```

**Secondary Colors**
```kotlin
MaterialTheme.colorScheme.secondary          // Supporting colors
MaterialTheme.colorScheme.onSecondary        // Text on secondary
MaterialTheme.colorScheme.secondaryContainer // Secondary containers
MaterialTheme.colorScheme.onSecondaryContainer
```

**Surface Colors**
```kotlin
MaterialTheme.colorScheme.surface           // Card backgrounds
MaterialTheme.colorScheme.onSurface         // Text on surface
MaterialTheme.colorScheme.surfaceVariant    // Alternative surfaces
MaterialTheme.colorScheme.onSurfaceVariant  // Text on variant
```

**Other Tokens**
```kotlin
MaterialTheme.colorScheme.background        // Screen background
MaterialTheme.colorScheme.error             // Error states
MaterialTheme.colorScheme.outline           // Borders, dividers
```

### Usage in Components

**Album Card**
```kotlin
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    modifier = Modifier.size(180.dp)
) {
    // Content
}
```

**Action Button**
```kotlin
FilledTonalButton(
    colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
) {
    Text("Download")
}
```

## Typography System

### Type Scale

```kotlin
val Typography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Normal
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )
)
```

### Usage Examples

```kotlin
// Song title
Text(
    text = song.title,
    style = MaterialTheme.typography.headlineMedium
)

// Artist name
Text(
    text = song.artist,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

// Section headers
Text(
    text = "Recent Albums",
    style = MaterialTheme.typography.titleLarge
)
```

## Components

### Scaffold

**Screen Structure**
```kotlin
@Composable
fun HomeScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NeoSynth") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Action */ }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->
        Content(Modifier.padding(paddingValues))
    }
}
```

### Cards

**Album Card**
```kotlin
@Composable
fun AlbumCard(album: Album) {
    Card(
        modifier = Modifier
            .size(180.dp)
            .clickable { /* Navigate */ },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column {
            AsyncImage(
                model = album.coverArt,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
```

### Buttons

**Filled Button (Primary Action)**
```kotlin
Button(
    onClick = { /* Action */ },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
) {
    Icon(Icons.Default.PlayArrow, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text("Play")
}
```

**Filled Tonal Button (Secondary Action)**
```kotlin
FilledTonalButton(
    onClick = { /* Action */ }
) {
    Icon(Icons.Default.Download, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text("Download")
}
```

**Outlined Button (Tertiary Action)**
```kotlin
OutlinedButton(
    onClick = { /* Action */ }
) {
    Text("Shuffle")
}
```

**Icon Button**
```kotlin
IconButton(onClick = { /* Action */ }) {
    Icon(
        Icons.Default.Favorite,
        contentDescription = "Favorite",
        tint = MaterialTheme.colorScheme.primary
    )
}
```

### Floating Action Button

**Standard FAB**
```kotlin
FloatingActionButton(
    onClick = { /* Action */ },
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Icon(Icons.Default.Add, contentDescription = "Add")
}
```

**Extended FAB**
```kotlin
ExtendedFloatingActionButton(
    onClick = { /* Action */ },
    icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
    text = { Text("Shuffle All") }
)
```

### Lists and Items

**LazyColumn with Sticky Headers**
```kotlin
@Composable
fun SongList(songs: List<Song>) {
    val groupedSongs = songs.groupBy { it.title.first().uppercaseChar() }
    
    LazyColumn {
        groupedSongs.forEach { (letter, songsInGroup) ->
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            items(songsInGroup) { song ->
                SongListItem(song)
            }
        }
    }
}
```

**List Item**
```kotlin
@Composable
fun SongListItem(song: Song) {
    ListItem(
        headlineContent = {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            AsyncImage(
                model = song.coverArt,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        },
        trailingContent = {
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier.clickable { /* Play song */ }
    )
}
```

### Navigation

**Top App Bar**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailTopBar(
    albumName: String,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = albumName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = { /* Favorite */ }) {
                Icon(Icons.Default.Favorite, contentDescription = "Favorite")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
```

### Dialogs

**Alert Dialog**
```kotlin
@Composable
fun DeleteConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Download")
        },
        text = {
            Text("Are you sure you want to delete this downloaded song?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

### Progress Indicators

**Circular Progress**
```kotlin
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}
```

**Linear Progress (Playback)**
```kotlin
@Composable
fun PlaybackProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit
) {
    Column {
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeek(it) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

### Surfaces and Elevation

**Surface with Elevation**
```kotlin
Surface(
    modifier = Modifier.size(120.dp),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primaryContainer,
    tonalElevation = 6.dp,
    shadowElevation = 4.dp
) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        )
    }
}
```

## Motion and Animation

### Enter/Exit Transitions

```kotlin
@Composable
fun AnimatedPlayerScreen(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it }
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it }
        ) + fadeOut()
    ) {
        FullPlayerScreen()
    }
}
```

### Animated Content

```kotlin
@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play"
            )
        }
    }
}
```

### Value Animation

```kotlin
@Composable
fun PulsatingFavoriteIcon(isFavorite: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Icon(
        Icons.Default.Favorite,
        contentDescription = "Favorite",
        tint = if (isFavorite) Color.Red else Color.Gray,
        modifier = Modifier.scale(scale)
    )
}
```

## Accessibility

### Content Descriptions

```kotlin
IconButton(onClick = { /* Skip next */ }) {
    Icon(
        Icons.Default.SkipNext,
        contentDescription = "Skip to next track"
    )
}
```

### Semantic Properties

```kotlin
Text(
    text = song.title,
    modifier = Modifier.semantics {
        heading()
        contentDescription = "Song title: ${song.title}"
    }
)
```

### Minimum Touch Targets

All interactive elements follow the 48dp minimum touch target size:

```kotlin
IconButton(
    onClick = { /* Action */ },
    modifier = Modifier.size(48.dp)
) {
    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
}
```

## Dark Theme Support

### Automatic Theme Detection

```kotlin
@Composable
fun NeoSynthApp() {
    NeoSynth_androidTheme(
        darkTheme = isSystemInDarkTheme(),
        dynamicColor = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainContent()
        }
    }
}
```

### Manual Theme Override

Users can override system theme in settings (future implementation):

```kotlin
var darkMode by remember { mutableStateOf(false) }

NeoSynth_androidTheme(
    darkTheme = darkMode,
    dynamicColor = true
) {
    // Content
}
```

## Responsive Design

### Adaptive Layouts

```kotlin
@Composable
fun AlbumGrid() {
    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp < 600 -> 2
        configuration.screenWidthDp < 840 -> 3
        else -> 4
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(albums) { album ->
            AlbumCard(album)
        }
    }
}
```

## Best Practices

1. **Use Semantic Colors:** Always use `MaterialTheme.colorScheme.*` instead of hardcoded colors
2. **Typography Tokens:** Use `MaterialTheme.typography.*` for consistent text styling
3. **Shape Tokens:** Use `MaterialTheme.shapes.*` for consistent corner radius
4. **Spacing:** Use multiples of 4dp for consistent spacing (4, 8, 12, 16, 24, 32)
5. **Elevation:** Use predefined elevation levels (0, 1, 2, 3, 4, 6, 8, 12, 16, 24dp)
6. **Accessibility:** Always provide content descriptions for icons
7. **State Management:** Use `remember` and `rememberSaveable` appropriately
8. **Performance:** Use `LazyColumn/LazyRow` for long lists
