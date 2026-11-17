package com.amir.mp3player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.amir.mp3player.databinding.ActivityMainBinding
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking = false

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            loadSongs()
        }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayButton()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNowPlaying()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateModeButtons()
        override fun onRepeatModeChanged(repeatMode: Int) = updateModeButtons()
        override fun onPlaybackStateChanged(playbackState: Int) = updateProgress()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SongAdapter { index -> playFrom(index) }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.btnPlay.setOnClickListener {
            val c = controller ?: return@setOnClickListener
            if (c.isPlaying) c.pause() else c.play()
        }
        binding.btnNext.setOnClickListener { controller?.seekToNext() }
        binding.btnPrev.setOnClickListener { controller?.seekToPrevious() }
        binding.btnShuffle.setOnClickListener {
            controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        }
        binding.btnRepeat.setOnClickListener {
            controller?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvElapsed.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                controller?.seekTo((seekBar?.progress ?: 0).toLong())
            }
        })

        requestNeededPermissions()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)
            updateAll()
        }, ContextCompat.getMainExecutor(this))
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) loadSongs() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun loadSongs() {
        songs = MusicRepository.loadSongs(this)
        adapter.submit(songs)
        binding.empty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun playFrom(index: Int) {
        val c = controller ?: return
        val items = songs.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    private fun updateAll() {
        updateNowPlaying()
        updatePlayButton()
        updateModeButtons()
        updateProgress()
    }

    private fun updateNowPlaying() {
        val c = controller ?: return
        val item = c.currentMediaItem
        binding.tvTitle.text = item?.mediaMetadata?.title ?: "Nothing playing"
        binding.tvArtist.text = item?.mediaMetadata?.artist ?: ""
        adapter.setCurrent(item?.mediaId?.toLongOrNull() ?: -1L)
    }

    private fun updatePlayButton() {
        val c = controller ?: return
        binding.btnPlay.setImageResource(
            if (c.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun updateModeButtons() {
        val c = controller ?: return
        binding.btnShuffle.alpha = if (c.shuffleModeEnabled) 1f else 0.4f
        when (c.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                binding.btnRepeat.text = "\uD83D\uDD02"
                binding.btnRepeat.alpha = 1f
            }
            Player.REPEAT_MODE_ALL -> {
                binding.btnRepeat.text = "\uD83D\uDD01"
                binding.btnRepeat.alpha = 1f
            }
            else -> {
                binding.btnRepeat.text = "\uD83D\uDD01"
                binding.btnRepeat.alpha = 0.4f
            }
        }
    }

    private fun updateProgress() {
        val c = controller ?: return
        if (userSeeking) return
        val dur = if (c.duration > 0) c.duration else 0L
        val pos = c.currentPosition.coerceAtLeast(0L)
        binding.seekBar.max = dur.toInt()
        binding.seekBar.progress = pos.toInt()
        binding.tvElapsed.text = formatTime(pos)
        binding.tvTotal.text = formatTime(dur)
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        return String.format("%d:%02d", total / 60, total % 60)
    }
}
