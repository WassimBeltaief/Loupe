package com.wassimbeltaief.loupe.sample

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class AlbumsViewModel : ViewModel() {

    private val _albums = MutableStateFlow(seedAlbums())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

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
