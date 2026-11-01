package dev.redmi9i.files.viewer

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.redmi9i.files.storage.Kind
import dev.redmi9i.files.storage.kindOf
import java.io.File

/** Video and audio. Playback stops when the screen closes (no background service in v1). */
class MediaViewerActivity : ViewerActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var status: TextView

    override fun onReady(savedInstanceState: Bundle?) {
        val isAudio = kindOf(ref.name) == Kind.AUDIO
        content.setBackgroundColor(Color.BLACK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply {
            if (isAudio) { controllerShowTimeoutMs = 0; controllerHideOnTouch = false }
        }
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; textSize = 16f
        }
        content.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
    }

    override fun onStart() {
        super.onStart()
        val p = ExoPlayer.Builder(this).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                status.text = "Cannot play this file\n(${error.errorCodeName})"
            }
        })
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(ref.path))))
        p.prepare()
        p.playWhenReady = true
        view.player = p
        if (kindOf(ref.name) == Kind.AUDIO) view.showController()
        player = p
    }

    override fun onStop() {
        view.player = null
        player?.release()
        player = null
        super.onStop()
    }
}
