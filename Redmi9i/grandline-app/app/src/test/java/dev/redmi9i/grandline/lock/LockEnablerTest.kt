package dev.redmi9i.grandline.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockEnablerTest {
    private val me = "dev.redmi9i.grandline/dev.redmi9i.grandline.lock.LockService"
    private val other = "com.android.settings/x.Menu"

    @Test fun addsToAnEmptyOrMissingSetting() {
        assertEquals(me, LockEnabler.withService(null, me))
        assertEquals(me, LockEnabler.withService("", me))
    }

    @Test fun keepsOtherServicesAndAppends() = assertEquals("$other:$me", LockEnabler.withService(other, me))

    @Test fun dropsTheStockMenuSoOursOwnsTheButton() =
        assertEquals("$other:$me", LockEnabler.withService("${LockEnabler.STOCK_MENU}:$other", me))

    @Test fun dropsTheStockMenuInItsShortFormToo() = assertEquals(
        me, LockEnabler.withService("com.android.settings/.accessibility.accessibilitymenu.AccessibilityMenuService:$me", me),
    )

    @Test fun neverAddsItTwice() = assertEquals("$other:$me", LockEnabler.withService("$other:$me", me))
}
