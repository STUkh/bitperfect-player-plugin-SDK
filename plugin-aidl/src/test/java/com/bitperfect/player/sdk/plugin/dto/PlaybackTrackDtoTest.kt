package com.bitperfect.player.sdk.plugin.dto

import com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PlaybackTrackDtoTest {
    @Test
    fun `API three callback methods append transaction IDs`() {
        assertEquals(2, transactionId("getCredential"))
        assertEquals(3, transactionId("putCredential"))
        assertEquals(4, transactionId("postEvent"))
        assertEquals(5, transactionId("clearCredentials"))
        assertEquals(6, transactionId("getRecentPlayback"))
        assertEquals(7, transactionId("requestPlayback"))
    }

    @Test
    fun `playback identity is not sender controlled`() {
        assertFalse(PlaybackTrackDto::class.java.declaredFields.any { it.name == "pluginId" })
    }

    private fun transactionId(method: String): Int {
        val field = IPluginHostCallbackV1.Stub::class.java.getDeclaredField("TRANSACTION_$method")
        field.isAccessible = true
        return field.getInt(null)
    }
}
