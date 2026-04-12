package com.github.codeplangui

import com.github.codeplangui.action.CommitPromptBuilder
import com.github.codeplangui.action.CommitPromptFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommitPromptBuilderTest {

    @Test
    fun `user message includes diff content`() {
        val diff = "diff --git a/Foo.kt b/Foo.kt\n+added line"
        val msg = CommitPromptBuilder.buildUserMessage(diff, "zh")
        assertTrue(msg.contains("diff --git"), "User message must contain the diff")
    }

    @Test
    fun `diff truncated at 5000 chars`() {
        val longDiff = "x".repeat(6000)
        val msg = CommitPromptBuilder.buildUserMessage(longDiff, "zh")
        assertFalse(msg.contains("x".repeat(5001)), "Should not contain more than 5000 x chars")
        assertTrue(msg.contains("[diff truncated]"), "Must include truncation marker")
    }

    @Test
    fun `diff under 5000 chars not truncated`() {
        val shortDiff = "short diff content"
        val msg = CommitPromptBuilder.buildUserMessage(shortDiff, "zh")
        assertFalse(msg.contains("[diff truncated]"))
        assertTrue(msg.contains(shortDiff))
    }

    @Test
    fun `system prompt contains zh when language is zh`() {
        val prompt = CommitPromptBuilder.buildSystemPrompt("zh")
        assertTrue(prompt.contains("中文"))
    }

    @Test
    fun `system prompt contains English when language is en`() {
        val prompt = CommitPromptBuilder.buildSystemPrompt("en")
        assertTrue(prompt.contains("English"))
    }

    @Test
    fun `buildDiffPreview summarizes selected file changes`() {
        val preview = CommitPromptBuilder.buildDiffPreview(
            listOf(
                CommitPromptFile(
                    path = "src/App.kt",
                    changeType = "MODIFICATION",
                    beforeContent = "line 1\nold line\nline 3",
                    afterContent = "line 1\nnew line\nline 3"
                ),
                CommitPromptFile(
                    path = "README.md",
                    changeType = "NEW",
                    beforeContent = null,
                    afterContent = "# Title\nbody"
                )
            )
        )

        assertTrue(preview.contains("=== MODIFICATION: src/App.kt ==="))
        assertTrue(preview.contains("- old line"))
        assertTrue(preview.contains("+ new line"))
        assertTrue(preview.contains("=== NEW: README.md ==="))
    }

    @Test
    fun `buildDiffPreview truncates oversized previews with marker`() {
        val largeContent = (1..2000).joinToString("\n") { "line-$it" }
        val files = (1..40).map { index ->
            CommitPromptFile(
                path = "src/LargeFile$index.kt",
                changeType = "NEW",
                beforeContent = null,
                afterContent = largeContent
            )
        }

        val preview = CommitPromptBuilder.buildDiffPreview(files)

        assertTrue(preview.contains("[diff truncated]"))
        assertFalse(preview.contains("src/LargeFile40.kt"))
    }
}
