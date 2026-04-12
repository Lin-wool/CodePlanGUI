package com.github.codeplangui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatServiceSettingsTest {

    @Test
    fun `openSettingsOnEdt schedules dialog instead of opening immediately`() {
        var opened = false
        var scheduled: (() -> Unit)? = null

        openSettingsOnEdt(
            openDialog = { opened = true },
            enqueue = { action -> scheduled = action }
        )

        assertFalse(opened)
        assertTrue(scheduled != null)

        scheduled!!.invoke()

        assertTrue(opened)
    }
}
