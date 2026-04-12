package com.github.codeplangui

import com.github.codeplangui.settings.PluginSettings
import com.github.codeplangui.settings.ProviderConfig
import com.github.codeplangui.settings.SettingsState
import com.github.codeplangui.settings.recoverLegacyProviderIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PluginSettingsTest {

    @Test
    fun `getActiveProvider returns provider matching active id`() {
        val first = ProviderConfig(id = "provider-1", name = "First", endpoint = "https://one.example/v1", model = "model-a")
        val second = ProviderConfig(id = "provider-2", name = "Second", endpoint = "https://two.example/v1", model = "model-b")

        val settings = PluginSettings()
        settings.loadState(
            SettingsState(
                providers = mutableListOf(first, second),
                activeProviderId = second.id
            )
        )

        assertEquals(second, settings.getActiveProvider())
    }

    @Test
    fun `getActiveProvider returns first provider when active id is stale`() {
        val settings = PluginSettings()
        val provider = ProviderConfig(id = "provider-1", name = "First", endpoint = "https://one.example/v1", model = "model-a")
        settings.loadState(
            SettingsState(
                providers = mutableListOf(provider),
                activeProviderId = "missing"
            )
        )

        assertEquals(provider, settings.getActiveProvider())
    }

    @Test
    fun `getActiveProvider falls back to first configured provider when active id is missing`() {
        val first = ProviderConfig(id = "provider-1", name = "First", endpoint = "https://one.example/v1", model = "model-a")
        val second = ProviderConfig(id = "provider-2", name = "Second", endpoint = "https://two.example/v1", model = "model-b")

        val settings = PluginSettings()
        settings.loadState(
            SettingsState(
                providers = mutableListOf(first, second),
                activeProviderId = "missing"
            )
        )

        assertEquals(first, settings.getActiveProvider())
    }

    @Test
    fun `recoverLegacyProviderIds reuses active provider id when legacy key exists there`() {
        val provider = ProviderConfig(id = "generated-id", name = "Only", endpoint = "https://one.example/v1", model = "model-a")

        val recovered = recoverLegacyProviderIds(
            providers = mutableListOf(provider),
            activeProviderId = "legacy-id",
            hasApiKey = { id -> id == "legacy-id" }
        )

        assertEquals("legacy-id", recovered.single().id)
    }

    @Test
    fun `recoverLegacyProviderIds keeps current id when no legacy key can be found`() {
        val provider = ProviderConfig(id = "provider-1", name = "Only", endpoint = "https://one.example/v1", model = "model-a")

        val recovered = recoverLegacyProviderIds(
            providers = mutableListOf(provider),
            activeProviderId = "missing",
            hasApiKey = { false }
        )

        assertEquals("provider-1", recovered.single().id)
    }

    @Test
    fun `recoverLegacyProviderIds keeps current id when key already exists on current provider`() {
        val provider = ProviderConfig(id = "provider-1", name = "Only", endpoint = "https://one.example/v1", model = "model-a")

        val recovered = recoverLegacyProviderIds(
            providers = mutableListOf(provider),
            activeProviderId = "legacy-id",
            hasApiKey = { id -> id == "provider-1" }
        )

        assertEquals("provider-1", recovered.single().id)
    }
}
