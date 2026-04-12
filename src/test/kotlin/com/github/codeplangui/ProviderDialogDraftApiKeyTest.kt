package com.github.codeplangui

import com.github.codeplangui.settings.resolveInitialApiKeyValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProviderDialogDraftApiKeyTest {

    @Test
    fun `resolveInitialApiKeyValue prefers pending draft over persisted value`() {
        val pending = mapOf("provider-1" to "draft-key")

        assertEquals(
            "draft-key",
            resolveInitialApiKeyValue("provider-1", pending, "persisted-key")
        )
    }

    @Test
    fun `resolveInitialApiKeyValue keeps explicit cleared draft`() {
        val pending = mapOf<String, String?>("provider-1" to null)

        assertNull(resolveInitialApiKeyValue("provider-1", pending, "persisted-key"))
    }

    @Test
    fun `resolveInitialApiKeyValue falls back to persisted value when there is no draft`() {
        assertEquals(
            "persisted-key",
            resolveInitialApiKeyValue("provider-1", emptyMap(), "persisted-key")
        )
    }
}
