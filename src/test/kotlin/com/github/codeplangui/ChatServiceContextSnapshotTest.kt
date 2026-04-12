package com.github.codeplangui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatServiceContextSnapshotTest {

    @Test
    fun `buildBaseSystemPrompt states command execution is unavailable`() {
        val prompt = buildBaseSystemPrompt()

        assertEquals(true, prompt.contains("没有终端"))
        assertEquals(true, prompt.contains("不要声称你已经执行命令"))
    }

    @Test
    fun `buildPromptContextSnapshot prefers selection and trims to configured max lines`() {
        val snapshot = buildPromptContextSnapshot(
            fileName = "package.json",
            extension = "json",
            selectedText = "first line\nsecond line",
            documentText = "document line 1\ndocument line 2",
            maxLines = 1
        )

        assertEquals("package.json", snapshot.fileName)
        assertEquals("json", snapshot.extension)
        assertEquals("first line", snapshot.content)
        assertEquals("package.json · 选中 2 行", snapshot.contextLabel)
    }

    @Test
    fun `buildPromptContextSnapshot labels full file context when no selection exists`() {
        val snapshot = buildPromptContextSnapshot(
            fileName = "ChatService.kt",
            extension = "kt",
            selectedText = null,
            documentText = "line 1\nline 2",
            maxLines = 20
        )

        assertEquals("ChatService.kt · 当前文件", snapshot.contextLabel)
    }

    @Test
    fun `formatSystemContent returns base prompt when no context snapshot exists`() {
        val base = buildBaseSystemPrompt()

        assertEquals(base, formatSystemContent(base, null))
    }

    @Test
    fun `formatSystemContent includes file name and fenced code block when context exists`() {
        val base = buildBaseSystemPrompt()
        val snapshot = PromptContextSnapshot(
            fileName = "package.json",
            extension = "json",
            content = "{\"name\":\"demo\"}",
            contextLabel = "package.json · 当前文件"
        )

        assertEquals(
            base + "\n\n当前文件：package.json\n```json\n{\"name\":\"demo\"}\n```",
            formatSystemContent(base, snapshot)
        )
    }

    @Test
    fun `resolveUiContextLabel prefers explicit selection label over prompt snapshot`() {
        val snapshot = PromptContextSnapshot(
            fileName = "ChatService.kt",
            extension = "kt",
            content = "fun demo() = Unit",
            contextLabel = "ChatService.kt · 当前文件"
        )

        assertEquals(
            "ChatService.kt · 选中 8 行",
            resolveUiContextLabel("ChatService.kt · 选中 8 行", snapshot)
        )
    }
}
