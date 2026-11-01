package dev.redmi9i.files.viewer

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** ImageView with pinch zoom, drag and double-tap zoom. */
class ZoomImageView(ctx: Context) : AppCompatImageView(ctx) {
    private val m = Matrix()
    private var fitScale = 1f
    private val maxFactor = 6f

    init { scaleType = ScaleType.MATRIX }

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = currentScale()
            val target = (cur * d.scaleFactor).coerceIn(fitScale * 0.9f, fitScale * maxFactor)
            val f = target / cur
            m.postScale(f, f, d.focusX, d.focusY)
            clamp(); imageMatrix = m
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(a: MotionEvent?, b: MotionEvent, dx: Float, dy: Float): Boolean {
            m.postTranslate(-dx, -dy); clamp(); imageMatrix = m; return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val cur = currentScale()
            val target = if (cur > fitScale * 1.5f) fitScale else fitScale * 3f
            val f = target / cur
            m.postScale(f, f, e.x, e.y); clamp(); imageMatrix = m
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) fit()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        fit()
    }

    private fun currentScale(): Float {
        val v = FloatArray(9); m.getValues(v); return v[Matrix.MSCALE_X]
    }

    private fun fit() {
        val d = drawable ?: return
        if (width == 0 || height == 0) return
        val dw = d.intrinsicWidth.toFloat(); val dh = d.intrinsicHeight.toFloat()
        fitScale = minOf(width / dw, height / dh)
        m.reset()
        m.postScale(fitScale, fitScale)
        m.postTranslate((width - dw * fitScale) / 2f, (height - dh * fitScale) / 2f)
        imageMatrix = m
    }

    /** Keep the image from being dragged off screen; centre it when smaller than the view. */
    private fun clamp() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(r)
        var dx = 0f; var dy = 0f
        dx = when {
            r.width() <= width -> (width - r.width()) / 2f - r.left
            r.left > 0 -> -r.left
            r.right < width -> width - r.right
            else -> 0f
        }
        dy = when {
            r.height() <= height -> (height - r.height()) / 2f - r.top
            r.top > 0 -> -r.top
            r.bottom < height -> height - r.bottom
            else -> 0f
        }
        m.postTranslate(dx, dy)
    }
}
