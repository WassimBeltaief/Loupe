package com.wassimbeltaief.loupe.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A stable album model — the grid is healthy and skippable. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    /** Index into the UI's cover palettes. */
    val paletteIndex: Int,
    val liked: Boolean,
    val likes: Int,
)

data class Comment(
    val id: Long,
    val author: String,
    val text: String,
)

/**
 * Deliberately bad sample state: the live listener counter is bundled with the
 * (static) comments, so anything that reads this object recomposes every second.
 */
data class AlbumDetailUiState(
    val comments: List<Comment>,
    val listeningNow: Int,
)

class AlbumsViewModel : ViewModel() {

    private val _albums = MutableStateFlow(seedAlbums())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _detail = MutableStateFlow(
        AlbumDetailUiState(comments = seedComments(), listeningNow = 128),
    )
    val detail: StateFlow<AlbumDetailUiState> = _detail.asStateFlow()

    init {
        // The unhappy path: the listener count ticks every second and is stored in
        // the same state as the comments, so the comments UI recomposes once a
        // second even though the comments never change.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                _detail.update { it.copy(listeningNow = it.listeningNow + 1) }
            }
        }
    }

    fun toggleLike(id: Long) {
        _albums.value = _albums.value.map { album ->
            if (album.id != id) {
                album
            } else {
                album.copy(
                    liked = !album.liked,
                    likes = album.likes + if (album.liked) -1 else 1,
                )
            }
        }
    }
}

private fun seedComments(): List<Comment> = listOf(
    Comment(1, "dana", "That bassline on track 3 is unreal."),
    Comment(2, "miles", "Been on repeat all week."),
    Comment(3, "yuki", "The vinyl pressing sounds warmer."),
    Comment(4, "sam", "Saw them live — this one hit different."),
)

private fun seedAlbums(): List<Album> = listOf(
    Album(1, "Midnight Signals", "Analog Dreams", 2024, 0, liked = false, likes = 248),
    Album(2, "Neon Coast", "The Satellites", 2023, 1, liked = true, likes = 1_204),
    Album(3, "Paper Planets", "Ivy Lane", 2022, 2, liked = false, likes = 87),
    Album(4, "Golden Hour", "Rosa Norte", 2024, 3, liked = false, likes = 512),
    Album(5, "Static Bloom", "Nova Kids", 2021, 4, liked = false, likes = 331),
    Album(6, "Deep Sleep", "Slow Tides", 2020, 5, liked = true, likes = 764),
    Album(7, "Rooftop Rain", "Cassette Club", 2023, 6, liked = false, likes = 156),
    Album(8, "Super 8", "The Weekenders", 2019, 7, liked = false, likes = 98),
)
