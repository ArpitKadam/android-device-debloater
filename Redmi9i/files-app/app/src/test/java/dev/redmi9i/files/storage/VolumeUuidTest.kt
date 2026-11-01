package dev.redmi9i.files.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeUuidTest {
    @Test fun removableVolumePathsGiveUuid() {
        assertEquals("1234-ABCD", volumeUuidOf("/storage/1234-ABCD"))
        assertEquals("1234-ABCD", volumeUuidOf("/storage/1234-ABCD/Movies/a.mp4"))
    }

    @Test fun internalAndOtherPathsGiveNull() {
        assertNull(volumeUuidOf("/storage/emulated/0/DCIM"))
        assertNull(volumeUuidOf("/storage/self/primary"))
        assertNull(volumeUuidOf("/sdcard/x"))
        assertNull(volumeUuidOf("/storage"))
        assertNull(volumeUuidOf("relative/path"))
    }
}
