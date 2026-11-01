package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

public class LockKeysTest {

    @Test
    public void dpadBackAndMenuOpenTheLock() {
        assertTrue(LockActivity.opensOn(KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(LockActivity.opensOn(KeyEvent.KEYCODE_DPAD_UP));
        assertTrue(LockActivity.opensOn(KeyEvent.KEYCODE_BACK));
        assertTrue(LockActivity.opensOn(KeyEvent.KEYCODE_MENU));
    }

    @Test
    public void volumePowerAndHomeAreLeftToTheSystem() {
        assertFalse(LockActivity.opensOn(KeyEvent.KEYCODE_VOLUME_UP));
        assertFalse(LockActivity.opensOn(KeyEvent.KEYCODE_VOLUME_DOWN));
        assertFalse(LockActivity.opensOn(KeyEvent.KEYCODE_VOLUME_MUTE));
        assertFalse(LockActivity.opensOn(KeyEvent.KEYCODE_POWER));
        assertFalse(LockActivity.opensOn(KeyEvent.KEYCODE_HOME));
    }
}
