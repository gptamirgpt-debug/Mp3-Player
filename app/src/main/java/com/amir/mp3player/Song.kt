package com.amir.mp3player

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val dateAdded: Long,
    val uri: Uri
)
