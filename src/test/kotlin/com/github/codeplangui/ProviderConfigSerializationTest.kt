package com.github.codeplangui

import com.github.codeplangui.settings.ProviderConfig
import com.github.codeplangui.settings.SettingsState
import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderConfigSerializationTest {

    @Test
    fun `provider config id survives xml serialization round trip`() {
        val state = SettingsState(
            providers = mutableListOf(
                ProviderConfig(
                    id = "provider-1",
                    name = "OpenAI",
                    endpoint = "https://api.example.com/v1/chat/completions",
                    model = "gpt-5.4"
                )
            ),
            activeProviderId = "provider-1"
        )

        val restored = XmlSerializer.deserialize(
            XmlSerializer.serialize(state),
            SettingsState::class.java
        )

        assertEquals("provider-1", restored.activeProviderId)
        assertEquals("provider-1", restored.providers.single().id)
    }
}
