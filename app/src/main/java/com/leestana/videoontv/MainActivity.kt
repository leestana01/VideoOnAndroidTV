package com.leestana.videoontv

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var emptyState: View
    private lateinit var quickActions: View
    private lateinit var boostButton: Button
    private lateinit var store: PlaybackStore
    private lateinit var relayServer: AudioRelayServer
    private var currentUri: Uri? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var boostPercent = 100

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persistAndPlay(it) }
    }

    private val openFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { showFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PlaybackStore(this)
        bindViews()
        createPlayer()
        relayServer = AudioRelayServer(contentResolver, { currentUri }, ::snapshot)
        runCatching { relayServer.start(10_000, false) }
        configureActions()
        intent?.data?.let(::play)
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player_view)
        emptyState = findViewById(R.id.empty_state)
        quickActions = findViewById(R.id.quick_actions)
        boostButton = findViewById(R.id.boost)
        findViewById<Button>(R.id.open_file).setOnClickListener { openFile.launch(arrayOf("video/*", "audio/*")) }
        findViewById<Button>(R.id.open_folder).setOnClickListener { openFolder.launch(null) }
    }

    private fun createPlayer() {
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        player = ExoPlayer.Builder(this, renderers)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                true,
            )
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEnhancer(audioSessionId)
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@MainActivity, readableError(error), Toast.LENGTH_LONG).show()
                }
            })
        }
        playerView.player = player
    }

    private fun configureActions() {
        findViewById<Button>(R.id.change_media).setOnClickListener { openFile.launch(arrayOf("video/*", "audio/*")) }
        boostButton.setOnClickListener { showBoostDialog() }
        findViewById<Button>(R.id.relay).setOnClickListener { showRelayDialog() }
    }

    private fun persistAndPlay(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        play(uri)
    }

    private fun play(uri: Uri) {
        store.save(currentUri, player.currentPosition)
        currentUri = uri
        emptyState.visibility = View.GONE
        quickActions.visibility = View.VISIBLE
        player.setMediaItem(MediaItem.fromUri(uri), store.load(uri))
        player.prepare()
        player.playWhenReady = true
        playerView.showController()
    }

    private fun showFolder(treeUri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val files = DocumentFile.fromTreeUri(this, treeUri)?.listFiles()
            ?.filter { it.isFile && (it.type?.startsWith("video/") == true || it.type?.startsWith("audio/") == true) }
            ?.sortedBy { it.name?.lowercase() }
            .orEmpty()
        if (files.isEmpty()) {
            Toast.makeText(this, "No playable media found in this folder", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Choose media")
            .setItems(files.map { it.name ?: "Untitled" }.toTypedArray()) { _, index -> play(files[index].uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBoostDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 12)
        }
        val value = TextView(this).apply { textSize = 24f; text = "$boostPercent%" }
        val warning = TextView(this).apply {
            text = "Levels above 100% may distort audio or damage speakers. Start low."
            setPadding(0, 18, 0, 8)
        }
        val seek = SeekBar(this).apply {
            max = 400
            progress = boostPercent
            keyProgressIncrement = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    boostPercent = progress
                    value.text = "$progress%"
                    applyBoost()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        container.addView(value)
        container.addView(seek)
        container.addView(warning)
        AlertDialog.Builder(this).setTitle("Audio booster").setView(container).setPositiveButton("Done", null).show()
    }

    private fun attachEnhancer(audioSessionId: Int) {
        loudnessEnhancer?.release()
        loudnessEnhancer = if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) null else runCatching {
            LoudnessEnhancer(audioSessionId).apply { enabled = true }
        }.getOrNull()
        applyBoost()
    }

    private fun applyBoost() {
        player.volume = AudioGain.playerVolume(boostPercent)
        runCatching { loudnessEnhancer?.setTargetGain(AudioGain.gainMillibels(boostPercent)) }
        boostButton.text = getString(R.string.audio_boost, boostPercent)
    }

    private fun showRelayDialog() {
        val url = relayServer.receiverUrl
        if (url == null) {
            Toast.makeText(this, "Connect the TV and phone to the same Wi-Fi network", Toast.LENGTH_LONG).show()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 20, 48, 8)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        layout.addView(ImageView(this).apply {
            setImageBitmap(qrCode(url, 480))
            contentDescription = "QR code for $url"
        }, LinearLayout.LayoutParams(480, 480))
        layout.addView(TextView(this).apply {
            text = "Scan with the phone on the same Wi-Fi network. The link expires when Vela closes.\n\n$url"
            textSize = 16f
            setTextIsSelectable(true)
        })
        AlertDialog.Builder(this)
            .setTitle("Use phone as a speaker")
            .setView(layout)
            .setNeutralButton("Mute TV") { _, _ -> player.volume = 0f }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun qrCode(text: String, size: Int): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bitmap ->
            for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF080B10.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    private fun snapshot() = PlaybackSnapshot(player.currentPosition.coerceAtLeast(0), player.isPlaying, player.playbackParameters.speed)

    private fun readableError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "This device cannot decode this video format. Try a lower-resolution or H.264 version."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The media file is no longer available."
        else -> "Playback failed (${error.errorCodeName})"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || currentUri == null) return super.dispatchKeyEvent(event)
        if (currentFocus is Button || currentFocus is SeekBar) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (player.isPlaying) player.pause() else player.play(); playerView.showController(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player.seekBack(); playerView.showController(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player.seekForward(); playerView.showController(); true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> { quickActions.requestFocus(); true }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onStop() {
        store.save(currentUri, player.currentPosition)
        super.onStop()
    }

    override fun onDestroy() {
        relayServer.stop()
        loudnessEnhancer?.release()
        player.release()
        super.onDestroy()
    }
}
