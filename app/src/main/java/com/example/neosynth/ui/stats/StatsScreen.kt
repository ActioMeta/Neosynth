package com.example.neosynth.ui.stats

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.neosynth.R
import com.example.neosynth.data.local.dao.ArtistTimeCount
import com.example.neosynth.data.local.dao.GenreTimeCount
import com.example.neosynth.data.local.dao.SongTimeCount

enum class CategoryType {
    SONGS, ARTISTS, GENRES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongsWithTime.collectAsStateWithLifecycle(initialValue = emptyList())
    val topArtists by viewModel.topArtistsWithTime.collectAsStateWithLifecycle(initialValue = emptyList())
    val topGenres by viewModel.topGenresWithTime.collectAsStateWithLifecycle(initialValue = emptyList())

    var detailedCategory by remember { mutableStateOf<CategoryType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.stats_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Carrusel horizontal de filtros
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(TimeFilter.values()) { filter ->
                    val isSelected = filter == activeFilter
                    val labelRes = when (filter) {
                        TimeFilter.DAY -> R.string.filter_today
                        TimeFilter.WEEK -> R.string.filter_week
                        TimeFilter.MONTH -> R.string.filter_month
                        TimeFilter.YEAR -> R.string.filter_year
                        TimeFilter.ALL -> R.string.filter_period_all
                    }
                    val interactionSource = remember { MutableInteractionSource() }
                    val scale by rememberBounceScale(interactionSource)

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setActiveFilter(filter) },
                        label = { Text(stringResource(labelRes), fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(16.dp),
                        interactionSource = interactionSource,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp)
            ) {
                // Sección 1: Top Canciones
                item {
                    CategorySection(
                        title = stringResource(R.string.stats_top_songs),
                        icon = Icons.Rounded.MusicNote,
                        onViewDetails = { detailedCategory = CategoryType.SONGS }
                    ) {
                        if (topSongs.isEmpty()) {
                            NoDataView()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topSongs.take(5).forEachIndexed { index, song ->
                                    SongStatRow(rank = index + 1, song = song)
                                }
                            }
                        }
                    }
                }

                // Sección 2: Top Artistas
                item {
                    CategorySection(
                        title = stringResource(R.string.stats_top_artists),
                        icon = Icons.Rounded.Person,
                        onViewDetails = { detailedCategory = CategoryType.ARTISTS }
                    ) {
                        if (topArtists.isEmpty()) {
                            NoDataView()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topArtists.take(5).forEachIndexed { index, artist ->
                                    ArtistStatRow(rank = index + 1, artist = artist)
                                }
                            }
                        }
                    }
                }

                // Sección 3: Top Géneros
                item {
                    CategorySection(
                        title = stringResource(R.string.stats_top_genres),
                        icon = Icons.Rounded.Category,
                        onViewDetails = { detailedCategory = CategoryType.GENRES }
                    ) {
                        if (topGenres.isEmpty()) {
                            NoDataView()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topGenres.take(5).forEachIndexed { index, genre ->
                                    GenreStatRow(rank = index + 1, genre = genre)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet para detalles de categoría
    detailedCategory?.let { category ->
        val title = when (category) {
            CategoryType.SONGS -> stringResource(R.string.stats_top_songs)
            CategoryType.ARTISTS -> stringResource(R.string.stats_top_artists)
            CategoryType.GENRES -> stringResource(R.string.stats_top_genres)
        }
        StatsDetailBottomSheet(
            title = title,
            categoryType = category,
            songs = topSongs,
            artists = topArtists,
            genres = topGenres,
            onDismiss = { detailedCategory = null }
        )
    }
}

@Composable
fun rememberBounceScale(interactionSource: InteractionSource): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounce_scale"
    )
}

@Composable
private fun CategorySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onViewDetails: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera de sección
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                val scale by rememberBounceScale(interactionSource)
                TextButton(
                    onClick = onViewDetails,
                    interactionSource = interactionSource,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                ) {
                    Text(
                        text = stringResource(R.string.stats_view_details),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun NoDataView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.stats_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val badgeColor = when (rank) {
        1 -> Color(0xFFFFD700) // Oro
        2 -> Color(0xFFC0C0C0) // Plata
        3 -> Color(0xFFCD7F32) // Bronce
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when (rank) {
        1, 2, 3 -> Color.Black
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(badgeColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun SongStatRow(rank: Int, song: SongTimeCount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RankBadge(rank = rank)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = stringResource(R.string.stats_minutes, song.totalTimeMs / 60000),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ArtistStatRow(rank: Int, artist: ArtistTimeCount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RankBadge(rank = rank)
        Text(
            text = artist.artist,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.stats_minutes, artist.totalTimeMs / 60000),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GenreStatRow(rank: Int, genre: GenreTimeCount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RankBadge(rank = rank)
        Text(
            text = genre.genre,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.stats_minutes, genre.totalTimeMs / 60000),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsDetailBottomSheet(
    title: String,
    categoryType: CategoryType,
    songs: List<SongTimeCount>,
    artists: List<ArtistTimeCount>,
    genres: List<GenreTimeCount>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (categoryType) {
                    CategoryType.SONGS -> {
                        itemsIndexed(songs) { index, song ->
                            SongStatRow(rank = index + 1, song = song)
                        }
                    }
                    CategoryType.ARTISTS -> {
                        itemsIndexed(artists) { index, artist ->
                            ArtistStatRow(rank = index + 1, artist = artist)
                        }
                    }
                    CategoryType.GENRES -> {
                        itemsIndexed(genres) { index, genre ->
                            GenreStatRow(rank = index + 1, genre = genre)
                        }
                    }
                }
            }
        }
    }
}
