package com.leestana.videoontv

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var emptyState: View
    private lateinit var quickActions: View
    private lateinit var boostButton: TextView
    private lateinit var openBrowserButton: TextView
    private lateinit var seekFeedback: TextView
    private lateinit var mediaBrowser: MediaBrowserPanel
    private lateinit var store: PlaybackStore
    private lateinit var relayServer: AudioRelayServer
    private val mainHandler = Handler(Looper.getMainLooper())
    private val snapshots = PlaybackSnapshotCache()
    @Volatile private var currentUri: Uri? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var enhancerSessionId = C.AUDIO_SESSION_ID_UNSET
    private var boostPercent = 100
    private val snapshotTicker = object : Runnable {
        override fun run() {
            updateSnapshot()
            mainHandler.postDelayed(this, SNAPSHOT_INTERVAL_MS)
        }
    }
    private val hideActions = Runnable {
        if (currentUri != null && player.playWhenReady && quickActions.visibility == View.VISIBLE) {
            val actionsHadFocus = quickActions.hasFocus()
            quickActions.visibility = View.GONE
            if (actionsHadFocus) playerView.requestFocus()
        }
    }
    private val hideSeekFeedback = Runnable { seekFeedback.visibility = View.GONE }

    private val openFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { openStorageRoot(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PlaybackStore(this)
        bindViews()
        createPlayer()
        relayServer = AudioRelayServer(contentResolver, { currentUri }, snapshots)
        runCatching { relayServer.start(10_000, false) }
        mainHandler.post(snapshotTicker)
        configureActions()
        configureBackNavigation()
        intent?.data?.let(::selectMedia)
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player_view)
        emptyState = findViewById(R.id.empty_state)
        quickActions = findViewById(R.id.quick_actions)
        boostButton = findViewById(R.id.boost)
        openBrowserButton = findViewById(R.id.open_browser)
        seekFeedback = findViewById(R.id.seek_feedback)
        mediaBrowser = MediaBrowserPanel(
            context = this,
            panel = findViewById(R.id.browser_panel),
            title = findViewById(R.id.browser_title),
            pathLabel = findViewById(R.id.browser_path),
            entriesView = findViewById(R.id.browser_entries),
            onMediaSelected = {
                mediaBrowser.hide()
                selectMedia(it.uri)
            },
            onChooseStorage = { openFolder.launch(null) },
        )
        openBrowserButton.setOnClickListener { browseStorage() }
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
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachEnhancer(audioSessionId)
                    applyBoost()
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (currentUri != null) showQuickActions(requestFocus = false, autoHide = playWhenReady)
                }
                override fun onEvents(player: Player, events: Player.Events) = updateSnapshot()
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@MainActivity, readableError(error), Toast.LENGTH_LONG).show()
                }
            })
        }
        playerView.player = player
    }

    private fun configureActions() {
        findViewById<TextView>(R.id.change_media).setOnClickListener {
            hideQuickActions()
            returnHome()
            browseStorage()
        }
        boostButton.setOnClickListener {
            hideQuickActions()
            showBoostDialog()
        }
        findViewById<TextView>(R.id.relay).setOnClickListener {
            hideQuickActions()
            showRelayDialog()
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mediaBrowser.navigateBack()) {
                    return
                } else if (currentUri != null) {
                    returnHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun selectMedia(uri: Uri) {
        val resumePosition = store.load(uri)
        if (!PlaybackPosition.canResume(resumePosition)) {
            play(uri, 0)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.resume_title)
            .setMessage(getString(R.string.resume_at, PlaybackPosition.format(resumePosition)))
            .setPositiveButton(R.string.resume) { _, _ -> play(uri, resumePosition) }
            .setNeutralButton(R.string.start_over) { _, _ ->
                store.clear(uri)
                play(uri, 0)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun play(uri: Uri, startPositionMs: Long) {
        store.save(currentUri, player.currentPosition)
        currentUri = uri
        emptyState.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        seekFeedback.visibility = View.GONE
        showQuickActions(requestFocus = false, autoHide = true)
        player.setMediaItem(MediaItem.fromUri(uri), startPositionMs)
        player.prepare()
        player.playWhenReady = true
        playerView.showController()
        updateSnapshot()
    }

    private fun browseStorage() {
        val savedRoot = store.loadStorageRoot()
        val document = savedRoot?.let { DocumentFile.fromTreeUri(this, it) }
        if (document?.exists() == true && document.isDirectory) {
            showMediaBrowser(document)
        } else {
            mediaBrowser.showWithoutRoot()
        }
    }

    private fun openStorageRoot(treeUri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val root = DocumentFile.fromTreeUri(this, treeUri)
        if (root == null || !root.isDirectory) {
            Toast.makeText(this, R.string.storage_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        store.saveStorageRoot(treeUri)
        showMediaBrowser(root)
    }

    private fun showMediaBrowser(root: DocumentFile) {
        mediaBrowser.show(root)
    }

    private fun returnHome() {
        store.save(currentUri, player.currentPosition)
        currentUri = null
        mainHandler.removeCallbacks(hideActions)
        mainHandler.removeCallbacks(hideSeekFeedback)
        player.pause()
        player.stop()
        player.clearMediaItems()
        playerView.hideController()
        playerView.visibility = View.GONE
        quickActions.visibility = View.GONE
        seekFeedback.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        mediaBrowser.hide()
        snapshots.update(0, false, 1f)
        openBrowserButton.requestFocus()
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
        val status = TextView(this).apply { setPadding(0, 8, 0, 0) }
        val seek = SeekBar(this).apply {
            max = 400
            progress = boostPercent
            keyProgressIncrement = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    boostPercent = progress
                    value.text = "$progress%"
                    status.text = boostStatus(progress, applyBoost())
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        container.addView(value)
        container.addView(seek)
        container.addView(warning)
        container.addView(status)
        status.text = boostStatus(boostPercent, applyBoost())
        AlertDialog.Builder(this).setTitle("Audio booster").setView(container).setPositiveButton("Done", null).show()
    }

    private fun attachEnhancer(audioSessionId: Int) {
        if (audioSessionId == enhancerSessionId && loudnessEnhancer != null) return
        loudnessEnhancer?.release()
        enhancerSessionId = C.AUDIO_SESSION_ID_UNSET
        loudnessEnhancer = if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) null else runCatching {
            LoudnessEnhancer(audioSessionId).also { enhancerSessionId = audioSessionId }
        }.getOrNull()
    }

    private fun applyBoost(): Boolean {
        player.volume = AudioGain.playerVolume(boostPercent)
        if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            attachEnhancer(player.audioSessionId)
        }
        val targetGain = AudioGain.gainMillibels(boostPercent)
        val applied = runCatching {
            loudnessEnhancer?.apply {
                enabled = false
                setTargetGain(targetGain)
                enabled = targetGain > 0
            }
        }.isSuccess && (targetGain == 0 || loudnessEnhancer?.enabled == true)
        boostButton.text = getString(R.string.audio_boost, boostPercent)
        return applied
    }

    private fun boostStatus(percent: Int, applied: Boolean): String = when {
        percent <= 100 -> "Standard volume"
        applied -> "Boost active: +${AudioGain.gainDecibels(percent)} dB"
        else -> "Audio boost is unavailable on this device or output."
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

    private fun updateSnapshot() {
        if (::player.isInitialized) {
            snapshots.update(player.currentPosition, player.isPlaying, player.playbackParameters.speed)
        }
    }

    private fun showQuickActions(requestFocus: Boolean, autoHide: Boolean) {
        quickActions.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideActions)
        if (requestFocus) boostButton.requestFocus()
        if (autoHide) mainHandler.postDelayed(hideActions, ACTIONS_TIMEOUT_MS)
    }

    private fun hideQuickActions() {
        mainHandler.removeCallbacks(hideActions)
        hideActions.run()
    }

    private fun showSeekFeedback(messageRes: Int) {
        seekFeedback.setText(messageRes)
        seekFeedback.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideSeekFeedback)
        mainHandler.postDelayed(hideSeekFeedback, SEEK_FEEDBACK_TIMEOUT_MS)
        playerView.showController()
    }

    private fun seekBack() {
        seekBy(-SEEK_INCREMENT_MS, R.string.seek_back_feedback)
    }

    private fun seekForward() {
        seekBy(SEEK_INCREMENT_MS, R.string.seek_forward_feedback)
    }

    private fun seekBy(deltaMs: Long, feedbackRes: Int) {
        val localMedia = currentUri?.scheme == "content" || currentUri?.scheme == "file"
        val canSeek = SeekCapability.canSeek(
            media3Seekable = player.isCurrentMediaItemSeekable,
            localMedia = localMedia,
            positionMs = player.currentPosition,
            durationMs = player.duration,
            playbackState = player.playbackState,
        )
        val target = SeekTarget.calculate(
            positionMs = player.currentPosition,
            durationMs = player.duration,
            deltaMs = deltaMs,
            isSeekable = canSeek,
        )
        if (target == null) {
            showSeekFeedback(R.string.seek_unavailable)
            return
        }
        player.seekTo(target)
        updateSnapshot()
        showSeekFeedback(feedbackRes)
    }

    private fun readableError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "This device cannot decode this video format. Try a lower-resolution or H.264 version."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The media file is no longer available."
        else -> "Playback failed (${error.errorCodeName})"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || currentUri == null) return super.dispatchKeyEvent(event)
        val quickActionNavigation = quickActions.visibility == View.VISIBLE && quickActions.hasFocus()
        if (quickActionNavigation && event.keyCode in DPAD_NAVIGATION_KEYS) {
            return super.dispatchKeyEvent(event)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play(); playerView.showController(); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBack(); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekForward(); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (currentFocus?.isClickable == true && currentFocus !== playerView) {
                    super.dispatchKeyEvent(event)
                } else {
                    if (player.isPlaying) player.pause() else player.play(); playerView.showController(); true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }
            KeyEvent.KEYCODE_DPAD_UP -> { showQuickActions(requestFocus = true, autoHide = player.playWhenReady); true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                showQuickActions(requestFocus = true, autoHide = player.playWhenReady); true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onStop() {
        store.save(currentUri, player.currentPosition)
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(snapshotTicker)
        mainHandler.removeCallbacks(hideActions)
        mainHandler.removeCallbacks(hideSeekFeedback)
        relayServer.stop()
        loudnessEnhancer?.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val SNAPSHOT_INTERVAL_MS = 250L
        private const val ACTIONS_TIMEOUT_MS = 5_000L
        private const val SEEK_FEEDBACK_TIMEOUT_MS = 800L
        private const val SEEK_INCREMENT_MS = 10_000L
        private val DPAD_NAVIGATION_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
        )
    }
}
