package com.github.codeplangui.action

data class CommitPromptFile(
    val path: String,
    val changeType: String,
    val beforeContent: String?,
    val afterContent: String?
)

object CommitPromptBuilder {

    private const val MAX_DIFF_CHARS = 5000
    private const val MAX_CHANGED_LINES = 40
    private const val MAX_NEW_FILE_CHARS = 1200

    fun buildSystemPrompt(language: String, format: String = "conventional"): String {
        val langInstruction = if (language == "zh") "中文" else "English"
        val formatInstruction = if (format == "freeform") {
            "- 格式：自由组织 subject 和 body，但保持简洁、可读、便于团队理解"
        } else {
            """
- 格式：<type>(<scope>): <subject>
- type 从以下选择：feat / fix / refactor / docs / test / chore / style / perf
            """.trimIndent()
        }
        return """
你是一个 git commit message 生成助手。
根据提供的 git diff，生成一条符合规范的 commit message。

要求：
$formatInstruction
- subject 使用${langInstruction}，不超过 72 字符
- 如需补充说明，在空行后加 body（bullet points）
- 只输出 commit message 本身，不要任何解释或额外文字
        """.trimIndent()
    }

    fun buildUserMessage(diff: String, language: String): String {
        val safeDiff = if (diff.length > MAX_DIFF_CHARS) {
            diff.take(MAX_DIFF_CHARS) + "\n... [diff truncated]"
        } else {
            diff
        }
        val languageName = if (language == "zh") "中文" else "English"
        return "请根据以下 git diff 生成 $languageName commit message：\n\n$safeDiff"
    }

    fun buildDiffPreview(files: List<CommitPromptFile>): String {
        if (files.isEmpty()) return ""

        val builder = StringBuilder()
        for ((index, file) in files.withIndex()) {
            if (index > 0) builder.append('\n')
            builder.append("=== ${file.changeType}: ${file.path} ===\n")
            builder.append(
                when (file.changeType) {
                    "NEW" -> previewNewFile(file.afterContent)
                    "DELETED" -> "--- file deleted\n"
                    else -> previewModifiedFile(file.beforeContent, file.afterContent)
                }
            )

            if (builder.length > MAX_DIFF_CHARS) {
                return builder.take(MAX_DIFF_CHARS).toString().trimEnd() + "\n... [diff truncated]"
            }
        }
        return builder.toString().trim()
    }

    private fun previewNewFile(content: String?): String {
        if (content.isNullOrBlank()) return "+++ [new file content unavailable]\n"

        val snippet = content.take(MAX_NEW_FILE_CHARS)
        val prefix = if (content.length > MAX_NEW_FILE_CHARS) {
            "+++ [new file truncated]\n"
        } else {
            "+++\n"
        }
        return prefix + snippet.lines().take(MAX_CHANGED_LINES).joinToString("\n") + "\n"
    }

    private fun previewModifiedFile(before: String?, after: String?): String {
        if (before == null || after == null) return "[modified content unavailable]\n"

        val beforeLines = before.lines()
        val afterLines = after.lines()
        val maxLines = maxOf(beforeLines.size, afterLines.size)
        val changed = mutableListOf<String>()

        for (index in 0 until maxLines) {
            if (changed.size >= MAX_CHANGED_LINES) break
            val beforeLine = beforeLines.getOrNull(index)
            val afterLine = afterLines.getOrNull(index)
            if (beforeLine == afterLine) continue

            if (!beforeLine.isNullOrEmpty()) {
                changed += "- $beforeLine"
            }
            if (!afterLine.isNullOrEmpty() && changed.size < MAX_CHANGED_LINES) {
                changed += "+ $afterLine"
            }
        }

        if (changed.isEmpty()) {
            return "[content changed but no compact diff available]\n"
        }
        if (maxLines > MAX_CHANGED_LINES) {
            changed += "... [more changes omitted]"
        }
        return changed.joinToString("\n", postfix = "\n")
    }
}
