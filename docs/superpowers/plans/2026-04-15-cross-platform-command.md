# Cross-Platform Command Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将硬编码的 Unix `sh -c` 执行层替换为 `ShellPlatform` 抽象，支持 Unix/macOS + Windows，同时覆盖路径校验、白名单默认值、AI 工具定义的跨平台差异。

**Architecture:** 新建 `ShellPlatform` sealed class，包含 `Unix` 和 `Windows` 两个 object 实现；`CommandExecutionService`、`ChatService`、`SettingsState`、`SettingsFormState` 各自委托给 `ShellPlatform.current()`，调用点改动最小。

**Tech Stack:** Kotlin, IntelliJ Plugin SDK, kotlinx.serialization, JUnit 5, MockK

---

## 文件结构

| 操作 | 路径 | 职责 |
|------|------|------|
| **新建** | `src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt` | 平台抽象 + Unix/Windows 实现 |
| **新建** | `src/test/kotlin/com/github/codeplangui/execution/ShellPlatformTest.kt` | ShellPlatform 纯函数单元测试 |
| **修改** | `src/main/kotlin/com/github/codeplangui/execution/CommandExecutionService.kt` | 委托 ProcessBuilder + 路径校验给 ShellPlatform |
| **修改** | `src/main/kotlin/com/github/codeplangui/ChatService.kt` | 工具定义 + system prompt 委托给 ShellPlatform |
| **修改** | `src/main/kotlin/com/github/codeplangui/settings/PluginSettings.kt` | 默认白名单改为 ShellPlatform.current().defaultWhitelist() |
| **修改** | `src/main/kotlin/com/github/codeplangui/settings/SettingsFormState.kt` | 同上 |

---

## Task 1: 创建 ShellPlatform — 纯函数部分（先测试）

**Files:**
- Create: `src/test/kotlin/com/github/codeplangui/execution/ShellPlatformTest.kt`
- Create: `src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt`

- [ ] **Step 1: 写失败测试（Unix extractBaseCommand）**

```kotlin
// src/test/kotlin/com/github/codeplangui/execution/ShellPlatformTest.kt
package com.github.codeplangui.execution

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShellPlatformTest {

    // ── Unix.extractBaseCommand ──────────────────────────────────────

    @Test
    fun `Unix extractBaseCommand returns first word`() {
        assertEquals("cargo", ShellPlatform.Unix.extractBaseCommand("cargo test --workspace"))
    }

    @Test
    fun `Unix extractBaseCommand strips unix path prefix`() {
        assertEquals("git", ShellPlatform.Unix.extractBaseCommand("/usr/bin/git status"))
    }

    @Test
    fun `Unix extractBaseCommand returns first word before pipe`() {
        assertEquals("ls", ShellPlatform.Unix.extractBaseCommand("ls src/ | grep kt"))
    }

    // ── Windows.extractBaseCommand ───────────────────────────────────

    @Test
    fun `Windows extractBaseCommand returns cmdlet name`() {
        assertEquals("Get-ChildItem", ShellPlatform.Windows.extractBaseCommand("Get-ChildItem src/"))
    }

    @Test
    fun `Windows extractBaseCommand strips windows path and exe suffix`() {
        assertEquals("git", ShellPlatform.Windows.extractBaseCommand("C:\\Program Files\\Git\\bin\\git.exe status"))
    }

    @Test
    fun `Windows extractBaseCommand strips forward-slash path`() {
        assertEquals("npm", ShellPlatform.Windows.extractBaseCommand("/c/Program Files/nodejs/npm install"))
    }

    // ── Unix.hasPathsOutsideWorkspace ────────────────────────────────

    @Test
    fun `Unix hasPathsOutside returns false for relative paths`() {
        assertFalse(ShellPlatform.Unix.hasPathsOutsideWorkspace("ls src/main", "/home/user/project"))
    }

    @Test
    fun `Unix hasPathsOutside returns true for absolute path outside project`() {
        assertTrue(ShellPlatform.Unix.hasPathsOutsideWorkspace("cat /etc/passwd", "/home/user/project"))
    }

    @Test
    fun `Unix hasPathsOutside returns false for absolute path inside project`() {
        assertFalse(ShellPlatform.Unix.hasPathsOutsideWorkspace(
            "cat /home/user/project/src/main.kt", "/home/user/project"
        ))
    }

    @Test
    fun `Unix hasPathsOutside returns false for flag tokens`() {
        assertFalse(ShellPlatform.Unix.hasPathsOutsideWorkspace("ls -la /home/user/project/src", "/home/user/project"))
    }

    // ── Windows.hasPathsOutsideWorkspace ─────────────────────────────

    @Test
    fun `Windows hasPathsOutside returns false for relative paths`() {
        assertFalse(ShellPlatform.Windows.hasPathsOutsideWorkspace("Get-ChildItem src\\main", "C:\\Users\\user\\project"))
    }

    @Test
    fun `Windows hasPathsOutside returns true for drive path outside project`() {
        assertTrue(ShellPlatform.Windows.hasPathsOutsideWorkspace(
            "Get-Content C:\\Windows\\System32\\drivers\\etc\\hosts",
            "C:\\Users\\user\\project"
        ))
    }

    @Test
    fun `Windows hasPathsOutside returns false for drive path inside project`() {
        assertFalse(ShellPlatform.Windows.hasPathsOutsideWorkspace(
            "Get-Content C:\\Users\\user\\project\\src\\main.kt",
            "C:\\Users\\user\\project"
        ))
    }

    @Test
    fun `Windows hasPathsOutside returns true for UNC path outside project`() {
        assertTrue(ShellPlatform.Windows.hasPathsOutsideWorkspace(
            "Get-Content \\\\server\\share\\secret.txt",
            "C:\\Users\\user\\project"
        ))
    }

    @Test
    fun `Windows hasPathsOutside returns false for flag tokens`() {
        assertFalse(ShellPlatform.Windows.hasPathsOutsideWorkspace(
            "Get-ChildItem -Recurse C:\\Users\\user\\project\\src",
            "C:\\Users\\user\\project"
        ))
    }

    // ── defaultWhitelist ─────────────────────────────────────────────

    @Test
    fun `Unix defaultWhitelist contains unix commands`() {
        val list = ShellPlatform.Unix.defaultWhitelist()
        assertTrue(list.containsAll(listOf("git", "ls", "cat", "grep", "find", "echo", "pwd")))
    }

    @Test
    fun `Windows defaultWhitelist contains powershell cmdlets`() {
        val list = ShellPlatform.Windows.defaultWhitelist()
        assertTrue(list.containsAll(listOf("git", "Get-ChildItem", "Get-Content", "Select-String")))
        assertFalse(list.contains("ls"))
        assertFalse(list.contains("cat"))
    }
}
```

- [ ] **Step 2: 运行测试，确认全部红（编译失败）**

```bash
./gradlew test --tests "com.github.codeplangui.execution.ShellPlatformTest" 2>&1 | tail -20
```

预期：编译错误，`ShellPlatform` 不存在。

- [ ] **Step 3: 实现 ShellPlatform（纯函数部分，暂不含 toolDefinition/shellHint）**

```kotlin
// src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt
package com.github.codeplangui.execution

import java.io.File

sealed class ShellPlatform {

    abstract fun buildProcess(command: String, workDir: File): ProcessBuilder
    abstract fun extractBaseCommand(command: String): String
    abstract fun hasPathsOutsideWorkspace(command: String, basePath: String): Boolean
    abstract fun toolName(): String
    abstract fun shellHint(): String
    abstract fun defaultWhitelist(): MutableList<String>

    object Unix : ShellPlatform() {

        override fun buildProcess(command: String, workDir: File): ProcessBuilder =
            ProcessBuilder("sh", "-c", command).directory(workDir)

        override fun extractBaseCommand(command: String): String {
            val base = command.trimStart().split(" ", "|", ";", ">", "<", "&").first().trim()
            return base.substringAfterLast('/')
        }

        override fun hasPathsOutsideWorkspace(command: String, basePath: String): Boolean {
            val home = System.getProperty("user.home") ?: ""
            return command.split("\\s+".toRegex()).any { token ->
                if (token.startsWith('-')) return@any false
                val expanded = if (token.startsWith("~/")) home + token.drop(1) else token
                if (!expanded.startsWith('/')) return@any false
                !expanded.startsWith(basePath)
            }
        }

        override fun toolName() = "run_command"

        override fun shellHint() = ""

        override fun defaultWhitelist() = mutableListOf(
            "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
            "git", "ls", "cat", "grep", "find", "echo", "pwd"
        )
    }

    object Windows : ShellPlatform() {

        override fun buildProcess(command: String, workDir: File): ProcessBuilder =
            ProcessBuilder("powershell", "-NoProfile", "-Command", command).directory(workDir)

        override fun extractBaseCommand(command: String): String {
            val base = command.trimStart().split(" ", "|", ";", ">", "<", "&", "&&").first().trim()
            return base.substringAfterLast('\\').substringAfterLast('/').removeSuffix(".exe")
        }

        override fun hasPathsOutsideWorkspace(command: String, basePath: String): Boolean {
            val normalizedBase = basePath.replace('/', '\\').trimEnd('\\')
            return command.split("\\s+".toRegex()).any { token ->
                if (token.startsWith('-')) return@any false
                val normalized = token.replace('/', '\\')
                val isAbsolute = normalized.matches(Regex("[A-Za-z]:\\\\.*")) || normalized.startsWith("\\\\")
                if (!isAbsolute) return@any false
                !normalized.startsWith(normalizedBase)
            }
        }

        override fun toolName() = "run_powershell"

        override fun shellHint() =
            "\n当前运行在 Windows 环境，请使用 PowerShell 语法调用 run_powershell 工具。"

        override fun defaultWhitelist() = mutableListOf(
            "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
            "git",
            "Get-ChildItem", "Get-Content", "Select-String",
            "Get-Location", "Write-Output", "Where-Object"
        )
    }

    companion object {
        fun current(): ShellPlatform =
            if (System.getProperty("os.name").lowercase().contains("win")) Windows else Unix
    }
}
```

- [ ] **Step 4: 运行测试，确认全绿**

```bash
./gradlew test --tests "com.github.codeplangui.execution.ShellPlatformTest" 2>&1 | tail -20
```

预期：所有测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt \
        src/test/kotlin/com/github/codeplangui/execution/ShellPlatformTest.kt
git commit -m "feat(execution): add ShellPlatform abstraction for cross-platform shell support"
```

---

## Task 2: 修改 CommandExecutionService 委托给 ShellPlatform

**Files:**
- Modify: `src/main/kotlin/com/github/codeplangui/execution/CommandExecutionService.kt`
- Modify: `src/test/kotlin/com/github/codeplangui/execution/CommandExecutionServiceTest.kt`

- [ ] **Step 1: 更新 CommandExecutionServiceTest — 删除已被 ShellPlatformTest 覆盖的测试**

`extractBaseCommand`、`isWhitelisted`、`hasPathsOutsideWorkspace` 的测试现在由 `ShellPlatformTest` 覆盖。删除 `CommandExecutionServiceTest` 中对应的测试，避免重复：

删除以下测试方法（保留 `executeAsyncWithStream` 系列）：
- `extractBaseCommand returns first word for simple command`
- `extractBaseCommand strips path prefix`
- `extractBaseCommand returns first word before pipe`
- `isWhitelisted returns true when base command matches whitelist entry`
- `isWhitelisted returns false when base command is not in whitelist`
- `isWhitelisted returns false for empty whitelist`
- `hasPathsOutsideWorkspace returns false for relative paths`
- `hasPathsOutsideWorkspace returns true for absolute path outside project`
- `hasPathsOutsideWorkspace returns false for absolute path inside project`

- [ ] **Step 2: 修改 CommandExecutionService.kt**

将 `executeAsync` 中 `ProcessBuilder("sh", "-c", command)` 替换（第 21 行）：

```kotlin
// 替换前
val process = ProcessBuilder("sh", "-c", command)
    .directory(File(basePath))
    .redirectErrorStream(false)
    .start()

// 替换后
val process = ShellPlatform.current().buildProcess(command, File(basePath))
    .redirectErrorStream(false)
    .start()
```

将 `executeAsyncWithStream` 中第 71 行做相同替换。

将 `companion object` 中的 `extractBaseCommand` 和 `hasPathsOutsideWorkspace` 委托给 `ShellPlatform`：

```kotlin
companion object {
    fun extractBaseCommand(command: String): String =
        ShellPlatform.current().extractBaseCommand(command)

    fun isWhitelisted(command: String, whitelist: List<String>): Boolean {
        if (whitelist.isEmpty()) return false
        val base = extractBaseCommand(command)
        return whitelist.any { it == base }
    }

    fun hasPathsOutsideWorkspace(command: String, basePath: String): Boolean =
        ShellPlatform.current().hasPathsOutsideWorkspace(command, basePath)

    fun truncateOutput(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take(maxChars)

    fun getInstance(project: Project): CommandExecutionService =
        project.getService(CommandExecutionService::class.java)
}
```

在文件顶部 import 列表加入：
```kotlin
import com.github.codeplangui.execution.ShellPlatform
```

- [ ] **Step 3: 运行全量测试**

```bash
./gradlew test 2>&1 | tail -30
```

预期：所有测试 PASS（`executeAsyncWithStream` 系列在 Unix CI 上继续绿，`ShellPlatformTest` 全绿）。

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/github/codeplangui/execution/CommandExecutionService.kt \
        src/test/kotlin/com/github/codeplangui/execution/CommandExecutionServiceTest.kt
git commit -m "refactor(execution): delegate shell invocation and path validation to ShellPlatform"
```

---

## Task 3: 给 ShellPlatform 加 toolDefinition()，修改 ChatService

**Files:**
- Modify: `src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt`
- Modify: `src/main/kotlin/com/github/codeplangui/ChatService.kt`

- [ ] **Step 1: 在 ShellPlatform 加 toolDefinition() 抽象方法及实现**

在 `ShellPlatform.kt` 顶部追加 import：
```kotlin
import com.github.codeplangui.api.FunctionDefinition
import com.github.codeplangui.api.ToolDefinition
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

在 sealed class 加抽象方法：
```kotlin
abstract fun toolDefinition(): ToolDefinition
```

在 `Unix` object 加实现：
```kotlin
override fun toolDefinition(): ToolDefinition = ToolDefinition(
    type = "function",
    function = FunctionDefinition(
        name = "run_command",
        description = "Execute a shell command in the project root directory. " +
            "Only use when the user asks you to run something or when you need to " +
            "inspect state to answer accurately.",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "The bash/shell command to execute")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "One-line explanation of why you are running this command")
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("command"))
                add(JsonPrimitive("description"))
            })
        }
    )
)
```

在 `Windows` object 加实现：
```kotlin
override fun toolDefinition(): ToolDefinition = ToolDefinition(
    type = "function",
    function = FunctionDefinition(
        name = "run_powershell",
        description = "Execute a PowerShell command in the project root directory. " +
            "Only use when the user asks you to run something or when you need to " +
            "inspect state to answer accurately. Use PowerShell syntax.",
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "The PowerShell command to execute")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "One-line explanation of why you are running this command")
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("command"))
                add(JsonPrimitive("description"))
            })
        }
    )
)
```

- [ ] **Step 2: 修改 ChatService.kt — runCommandToolDefinition()**

找到（约第 232 行）`private fun runCommandToolDefinition(): ToolDefinition = ToolDefinition(...)` 整个函数，替换为：

```kotlin
private fun runCommandToolDefinition(): ToolDefinition =
    ShellPlatform.current().toolDefinition()
```

在文件顶部 import 列表加：
```kotlin
import com.github.codeplangui.execution.ShellPlatform
```

- [ ] **Step 3: 修改 ChatService.kt — buildBaseSystemPrompt()**

找到（约第 750 行）`internal fun buildBaseSystemPrompt(...)` 中 `commandExecutionEnabled = true` 的分支，将工具名和平台提示改为动态：

```kotlin
internal fun buildBaseSystemPrompt(commandExecutionEnabled: Boolean = false): String =
    if (commandExecutionEnabled) {
        val platform = ShellPlatform.current()
        """
你是一个代码助手。请简洁准确地回答用户问题。
你拥有 ${platform.toolName()} 工具，可以在用户项目根目录执行命令。
当用户请求运行命令、查看文件、执行构建或测试时，主动调用该工具获取真实结果后再作答。${platform.shellHint()}
        """.trimIndent()
    } else {
        """
你是一个代码助手。请简洁准确地回答用户问题。
你当前没有终端、文件系统或工具调用能力。
不要声称你已经执行命令、读取文件、修改代码或查看了运行结果。
如果用户要求你直接运行命令或检查本地文件，请明确说明当前插件暂不支持该能力，并要求用户粘贴结果或手动提供内容。
        """.trimIndent()
    }
```

- [ ] **Step 4: 检查 FunctionDefinition import 是否已在 ChatService 中存在**

```bash
grep "FunctionDefinition" src/main/kotlin/com/github/codeplangui/ChatService.kt
```

如果存在，说明 `ChatService` 原来 import 了 `FunctionDefinition`，现在 `runCommandToolDefinition` 已经移走，检查是否有其他用途。如果没有其他用途，删除该 import。

- [ ] **Step 5: 运行全量测试**

```bash
./gradlew test 2>&1 | tail -30
```

预期：所有测试 PASS。

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt \
        src/main/kotlin/com/github/codeplangui/ChatService.kt
git commit -m "feat(execution): wire ShellPlatform tool definition and system prompt into ChatService"
```

---

## Task 4: 更新默认白名单

**Files:**
- Modify: `src/main/kotlin/com/github/codeplangui/settings/PluginSettings.kt`
- Modify: `src/main/kotlin/com/github/codeplangui/settings/SettingsFormState.kt`
- Modify: `src/test/kotlin/com/github/codeplangui/PluginSettingsTest.kt`（如存在白名单相关断言）

- [ ] **Step 1: 检查现有测试是否硬编码了白名单内容**

```bash
grep -n "commandWhitelist\|ls\|cat\|grep\|find\|echo\|pwd" \
  src/test/kotlin/com/github/codeplangui/PluginSettingsTest.kt \
  src/test/kotlin/com/github/codeplangui/SettingsFormStateTest.kt 2>/dev/null
```

如果测试中有断言默认白名单包含 `"ls"` 等 Unix 命令，在下一步改完实现后需同步更新测试断言——改为验证 `ShellPlatform.current().defaultWhitelist()` 的内容。

- [ ] **Step 2: 修改 PluginSettings.kt 中 SettingsState 的默认值**

找到（约第 33 行）：
```kotlin
var commandWhitelist: MutableList<String> = mutableListOf(
    "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
    "git", "ls", "cat", "grep", "find", "echo", "pwd"
),
```

替换为：
```kotlin
var commandWhitelist: MutableList<String> = ShellPlatform.current().defaultWhitelist(),
```

在文件顶部加 import：
```kotlin
import com.github.codeplangui.execution.ShellPlatform
```

- [ ] **Step 3: 修改 SettingsFormState.kt 中的默认值**

找到（约第 17 行）：
```kotlin
var commandWhitelist: MutableList<String> = mutableListOf(
    "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
    "git", "ls", "cat", "grep", "find", "echo", "pwd"
),
```

替换为：
```kotlin
var commandWhitelist: MutableList<String> = ShellPlatform.current().defaultWhitelist(),
```

在文件顶部加 import：
```kotlin
import com.github.codeplangui.execution.ShellPlatform
```

- [ ] **Step 4: 运行全量测试，修复受影响断言**

```bash
./gradlew test 2>&1 | tail -30
```

如有测试因白名单默认值改变而失败，将断言改为：
```kotlin
// 改前
assertTrue(state.commandWhitelist.contains("ls"))
// 改后
assertTrue(state.commandWhitelist.containsAll(ShellPlatform.current().defaultWhitelist()))
```

再次运行确认全绿：
```bash
./gradlew test 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/github/codeplangui/settings/PluginSettings.kt \
        src/main/kotlin/com/github/codeplangui/settings/SettingsFormState.kt \
        src/test/kotlin/com/github/codeplangui/PluginSettingsTest.kt \
        src/test/kotlin/com/github/codeplangui/SettingsFormStateTest.kt
git commit -m "feat(settings): use platform-aware default command whitelist"
```

---

## Self-Review 检查记录

**Spec coverage:**
- ✅ `ProcessBuilder("sh", "-c", ...)` 替换 → Task 2
- ✅ `extractBaseCommand` 跨平台 → Task 1 + Task 2
- ✅ `hasPathsOutsideWorkspace` 跨平台 → Task 1 + Task 2
- ✅ 工具定义（`run_command` vs `run_powershell`）→ Task 3
- ✅ system prompt 工具名动态化 → Task 3
- ✅ 默认白名单跨平台 → Task 4

**Placeholder scan:** 无 TBD / TODO / "类似上面"。

**Type consistency:**
- `ShellPlatform.toolDefinition()` 在 Task 1 定义抽象，Task 3 实现，Task 3 在 `ChatService` 中调用 — 一致
- `ShellPlatform.current().defaultWhitelist()` 在 Task 1 定义，Task 4 调用 — 一致
- `CommandExecutionService.companion` 中 `extractBaseCommand` / `hasPathsOutsideWorkspace` 签名不变，调用方 `ChatService` 无需改动 — 一致
