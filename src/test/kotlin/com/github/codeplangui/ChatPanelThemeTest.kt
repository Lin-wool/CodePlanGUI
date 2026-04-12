package com.github.codeplangui

import com.intellij.ide.ui.LafManagerListener
import com.intellij.util.messages.Topic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatPanelThemeTest {

    @Test
    fun `bridgeTheme returns dark for darcula`() {
        assertEquals("dark", bridgeTheme(isUnderDarcula = true))
    }

    @Test
    fun `bridgeTheme returns light for non darcula`() {
        assertEquals("light", bridgeTheme(isUnderDarcula = false))
    }

    @Test
    fun `laf topic requires application bus subscription`() {
        assertEquals(Topic.BroadcastDirection.NONE, LafManagerListener.TOPIC.broadcastDirection)
        assertTrue(themeTopicRequiresApplicationBus())
    }
}
