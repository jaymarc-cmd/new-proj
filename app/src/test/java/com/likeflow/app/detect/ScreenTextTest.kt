package com.likeflow.app.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTextTest {

    @Test
    fun `parses elapsed and total time`() {
        assertEquals(12f / 45f, ScreenText.parseTimeProgress("0:12 / 0:45")!!, 1e-4f)
        assertEquals(0.5f, ScreenText.parseTimeProgress("Video progress 01:00/02:00")!!, 1e-4f)
        assertEquals(0.5f, ScreenText.parseTimeProgress("0:30:00 / 1:00:00")!!, 1e-4f)
    }

    @Test
    fun `rejects nonsense time labels`() {
        assertNull(ScreenText.parseTimeProgress("Liked by 12 people"))
        assertNull(ScreenText.parseTimeProgress("0:50 / 0:45"))
        assertNull(ScreenText.parseTimeProgress("0:00 / 0:00"))
        assertNull(ScreenText.parseTimeProgress("0:75 / 1:00"))
        assertNull(ScreenText.parseTimeProgress(null))
    }

    @Test
    fun `identity ignores counts and order`() {
        val a = ScreenText.videoIdentity(listOf("@creator", "Morning routine #fyp", "12.4K"))
        val b = ScreenText.videoIdentity(listOf("12.5K", "Morning routine #fyp", "@creator"))
        assertEquals(a, b)
        assertNotEquals(a, ScreenText.videoIdentity(listOf("@someone_else", "Another caption")))
        assertNull(ScreenText.videoIdentity(listOf("1,024", "")))
    }
}
