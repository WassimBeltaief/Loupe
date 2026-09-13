package com.wassimbeltaief.loupe.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wassimbeltaief.loupe.runtime.LoupeIgnore

// ── Theme ────────────────────────────────────────────────────────────────────

private val LoupeBg = Color(0xFFF7F8F9)
private val LoupeSurface = Color(0xFFFFFFFF)
private val LoupeSurfaceMuted = Color(0xFFF2F3F5)
private val LoupeInk = Color(0xFF2B2B2B)
private val LoupeMuted = Color(0xFF8A8A8A)
private val LoupeOutline = Color(0xFFECEDEF)
private val LoupeGreen = Color(0xFF7FB69B)
private val LoupeAlert = Color(0xFFD98B8B)

private val RedditTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

@Composable
fun LoupeSampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = LoupeGreen,
            onPrimary = Color.White,
            background = LoupeBg,
            onBackground = LoupeInk,
            surface = LoupeSurface,
            onSurface = LoupeInk,
            surfaceVariant = LoupeSurfaceMuted,
            onSurfaceVariant = LoupeMuted,
            outline = LoupeOutline,
            error = LoupeAlert,
        ),
        typography = RedditTypography,
        content = content,
    )
}

// ── Album app ────────────────────────────────────────────────────────────────

@Composable
@LoupeIgnore
fun AlbumApp(viewModel: AlbumsViewModel = viewModel()) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }

    // Stable callbacks. Without `remember` these are recreated on every
    // recomposition, so every AlbumCard sees a new lambda param, loses
    // skippability, and all albums recompose when you like just one.
    val onOpen = remember { { id: Long -> selectedId = id } }
    val onBack = remember { { selectedId = null } }
    val onLike = remember(viewModel) { { id: Long -> viewModel.toggleLike(id) } }

    val selected = albums.firstOrNull { it.id == selectedId }
    if (selected == null) {
        AlbumGridScreen(
            albums = albums,
            onOpen = onOpen,
            onLike = onLike,
        )
    } else {
        AlbumDetailScreen(
            album = selected,
            onBack = onBack,
            onLike = onLike,
        )
    }
}

@Composable
fun AlbumGridScreen(
    albums: List<Album>,
    onOpen: (Long) -> Unit,
    onLike: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoupeBg),
    ) {
        TopBar(title = "Albums", subtitle = "${albums.size} albums")
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onOpen = onOpen,
                    onLike = onLike,
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    onOpen: (Long) -> Unit,
    onLike: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoupeSurface)
            .border(1.dp, LoupeOutline, RoundedCornerShape(16.dp))
            .clickable { onOpen(album.id) }
            .padding(10.dp),
    ) {
        AlbumCover(
            paletteIndex = album.paletteIndex,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium,
            color = LoupeInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.artist} · ${album.year}",
            style = MaterialTheme.typography.labelSmall,
            color = LoupeMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        LikeButton(
            likes = album.likes,
            liked = album.liked,
            // Remembered per album id: each card owns one stable click lambda,
            // so untouched cards keep skipping when a single like changes.
            onClick = remember(album.id) { { onLike(album.id) } },
        )
    }
}

// ── Album detail ─────────────────────────────────────────────────────────────

@Composable
fun AlbumDetailScreen(album: Album, onBack: () -> Unit, onLike: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoupeBg)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        TopBar(title = "Album", subtitle = album.artist, onBack = onBack)
        Column(modifier = Modifier.padding(16.dp)) {
            AlbumCover(
                paletteIndex = album.paletteIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.headlineSmall,
                color = LoupeInk,
            )
            Text(
                text = "${album.artist} · ${album.year}",
                style = MaterialTheme.typography.bodyMedium,
                color = LoupeMuted,
            )

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(LoupeSurface)
                    .border(1.dp, LoupeOutline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = "LIKES",
                    style = MaterialTheme.typography.labelSmall,
                    color = LoupeMuted,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LikeText(album)
                    Spacer(Modifier.weight(1f))
                    LikeButton(
                        likes = album.likes,
                        liked = album.liked,
                        compact = true,
                        onClick = remember(album.id) { { onLike(album.id) } },
                    )
                }
            }
        }
    }
}

// ── Components ───────────────────────────────────────────────────────────────

@Composable
private fun TopBar(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LoupeSurface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Text(
                    text = "←",
                    color = LoupeInk,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 8.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = LoupeInk,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = LoupeMuted,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LoupeOutline),
        )
    }
}

@Composable
private fun AlbumCover(paletteIndex: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(coverColor(paletteIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "♪",
            color = Color(0xFFC4C8CC),
            fontSize = 44.sp,
        )
    }
}

@Composable
private fun LikeButton(
    likes: Int,
    liked: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (liked) LoupeGreen.copy(alpha = 0.14f) else LoupeSurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (liked) "♥" else "♡",
            color = if (liked) LoupeGreen else LoupeMuted,
            fontSize = if (compact) 18.sp else 14.sp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$likes",
            style = MaterialTheme.typography.labelMedium,
            color = if (liked) LoupeGreen else LoupeMuted,
        )
    }
}

@Composable
private fun LikeText(album: Album) {
    Text(
        text = "${album.likes}",
        style = MaterialTheme.typography.headlineMedium,
        color = LoupeGreen,
    )
}


// ── Plain, very light pastel covers ──────────────────────────────────────────

private val CoverColors = listOf(
    Color(0xFFE9EEF3),
    Color(0xFFE7F1EB),
    Color(0xFFF3EDE6),
    Color(0xFFF0EAF3),
    Color(0xFFE8EFF6),
    Color(0xFFF4F0E8),
    Color(0xFFEDEFF1),
    Color(0xFFEAF3EC),
)

private fun coverColor(index: Int): Color = CoverColors[index % CoverColors.size]
