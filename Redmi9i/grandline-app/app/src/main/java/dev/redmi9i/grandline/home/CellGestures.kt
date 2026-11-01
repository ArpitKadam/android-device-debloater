package dev.redmi9i.grandline.home

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Tap, long-press and drag for one icon.
 * - quick tap: [onTap]
 * - long-press and release without moving: [onMenu]
 * - long-press and then move: [onDrag] starts a system drag-and-drop
 */
class CellGestures(
    private val view: View,
    private val onTap: () -> Unit,
    private val onMenu: () -> Unit,
    private val onDrag: () -> Unit,
) {
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var dragStarted = false
    private var moved = false

    private val longPress = Runnable {
        longPressed = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        view.parent?.requestDisallowInterceptTouchEvent(true)
    }

    fun install() {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    longPressed = false; dragStarted = false; moved = false
                    v.isPressed = true
                    v.postDelayed(longPress, longPressMs)
                }
                MotionEvent.ACTION_MOVE -> {
                    val far = abs(e.x - downX) > slop || abs(e.y - downY) > slop
                    if (far) moved = true
                    if (far && !longPressed) { v.removeCallbacks(longPress); v.isPressed = false }
                    if (far && longPressed && !dragStarted) { dragStarted = true; v.isPressed = false; onDrag() }
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPress); v.isPressed = false
                    if (!longPressed && !moved) { v.performClick(); onTap() }
                    else if (longPressed && !dragStarted) onMenu()
                }
                MotionEvent.ACTION_CANCEL -> { v.removeCallbacks(longPress); v.isPressed = false }
            }
            true
        }
    }
}
