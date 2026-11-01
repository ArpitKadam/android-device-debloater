package dev.redmi9i.grandline.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockViewTest {
    @Test fun aQuarterScreenDragOpens() = assertTrue(LockView.shouldOpen(draggedUp = 420f, screenH = 1600f, upSpeed = 0f))
    @Test fun aSmallSlowDragSpringsBack() = assertFalse(LockView.shouldOpen(draggedUp = 200f, screenH = 1600f, upSpeed = 300f))
    @Test fun aFastShortFlingOpens() = assertTrue(LockView.shouldOpen(draggedUp = 120f, screenH = 1600f, upSpeed = 2500f))
    @Test fun aFastFlingDownwardNeverOpens() = assertFalse(LockView.shouldOpen(draggedUp = 0f, screenH = 1600f, upSpeed = 3000f))
    @Test fun aTapDoesNotOpen() = assertFalse(LockView.shouldOpen(draggedUp = 3f, screenH = 1600f, upSpeed = 5000f))
}
