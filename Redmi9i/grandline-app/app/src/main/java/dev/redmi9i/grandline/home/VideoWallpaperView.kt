package dev.redmi9i.grandline.home

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import java.io.File

/**
 * Plays one muted looping video, cover-cropped to the view. Only one MediaPlayer ever exists, and it is
 * released on [stop], so a paused or hidden wallpaper costs nothing. The view is transparent until the first
 * video frame, so a still frame placed underneath shows meanwhile.
 */
class VideoWallpaperView(ctx: Context) : TextureView(ctx), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var file: File? = null
    private var surface: Surface? = null

    init { surfaceTextureListener = this }

    /** Start (or restart) [f]. Safe to call repeatedly with the same file. */
    fun play(f: File) {
        if (file == f && player != null) return
        stop()
        file = f
        startIfReady()
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        file = null
    }

    private fun startIfReady() {
        val f = file ?: return
        val s = surface ?: return
        if (player != null || !f.isFile) return
        player = MediaPlayer().apply {
            runCatching {
                setDataSource(f.path)
                setSurface(s)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, w, h -> cover(w, h) }
                setOnErrorListener { mp, _, _ -> mp.release(); player = null; true }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }.onFailure { release(); player = null }
        }
    }

    /** TextureView stretches the video to the view; undo that and scale so the video fills it, cropping the overflow. */
    private fun cover(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || width == 0 || height == 0) return
        val videoAspect = vw.toFloat() / vh
        val viewAspect = width.toFloat() / height
        val m = Matrix()
        if (videoAspect > viewAspect) m.setScale(videoAspect / viewAspect, 1f, width / 2f, height / 2f)
        else m.setScale(1f, viewAspect / videoAspect, width / 2f, height / 2f)
        setTransform(m)
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { surface = Surface(st); startIfReady() }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        val f = file
        stop(); file = f // keep the file so the video resumes when the surface comes back
        surface?.release(); surface = null
        return true
    }
}
