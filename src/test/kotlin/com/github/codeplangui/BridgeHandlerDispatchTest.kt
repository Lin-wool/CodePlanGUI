package com.github.codeplangui

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class BridgeHandlerDispatchTest {

    @Test
    fun `dispatchBridgeRequest routes sendMessage with includeContext`() {
        val commands = RecordingBridgeCommands()

        dispatchBridgeRequest(
            type = "sendMessage",
            text = "hello",
            includeContext = true,
            commands = commands
        )

        assertEquals(listOf(SentMessage("hello", true)), commands.sentMessages)
    }

    @Test
    fun `dispatchBridgeRequest routes frontendReady`() {
        val commands = RecordingBridgeCommands()

        dispatchBridgeRequest(
            type = "frontendReady",
            text = "",
            includeContext = false,
            commands = commands
        )

        assertEquals(1, commands.frontendReadyCalls)
    }

    @Test
    fun `handleBridgePayload reports bridge command failures instead of swallowing them`() {
        val result = handleBridgePayload(
            payload = """{"type":"sendMessage","text":"hello","includeContext":true}""",
            json = Json { ignoreUnknownKeys = true },
            commands = object : BridgeCommands {
                override fun sendMessage(text: String, includeContext: Boolean) {
                    throw IllegalStateException("boom")
                }

                override fun newChat() = Unit

                override fun openSettings() = Unit

                override fun onFrontendReady() = Unit
            }
        )

        val error = assertInstanceOf(BridgePayloadHandlingResult.CommandError::class.java, result)
        assertEquals("发送消息失败：boom", error.message)
    }

    private class RecordingBridgeCommands : BridgeCommands {
        val sentMessages = mutableListOf<SentMessage>()
        var frontendReadyCalls: Int = 0

        override fun sendMessage(text: String, includeContext: Boolean) {
            sentMessages += SentMessage(text, includeContext)
        }

        override fun newChat() = Unit

        override fun openSettings() = Unit

        override fun onFrontendReady() {
            frontendReadyCalls += 1
        }
    }
}

data class SentMessage(val text: String, val includeContext: Boolean)
