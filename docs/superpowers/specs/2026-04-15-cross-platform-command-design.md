# Cross-Platform Command Execution Design

**Date:** 2026-04-15  
**Scope:** `execution/`, `ChatService.kt`, `settings/PluginSettings.kt`, `settings/SettingsFormState.kt`

---

## 背景

`CommandExecutionService.kt:21,71` 硬编码 `ProcessBuilder("sh", "-c", command)`，仅支持 Unix/macOS。目标是支持 Unix/macOS + Windows，并建立统一抽象，后续跨平台扩展点全部走该抽象。

参考：Claude Code（TypeScript）采用双工具方案——`BashTool`（Unix）和 `PowerShellTool`（Windows），各自独立的工具定义、权限校验、系统提示。本设计遵循同一思路。

---

## 平台差异盘点

### 确认的差异点（全在 `CommandExecutionService.kt`）

| 行号 | 问题 | Unix | Windows |
|------|------|------|---------|
| `:21` `:71` | shell 调用方式 | `sh -c <cmd>` | `powershell -NoProfile -Command <cmd>` |
| `:136` | `extractBaseCommand` 路径分隔符 | `substringAfterLast('/')` | 需同时处理 `\` 和 `.exe` 后缀 |
| `:147–151` | `hasPathsOutsideWorkspace` 绝对路径检测 | `startsWith("/")`, `~/` 展开 | `C:\...` 或 `\\UNC`，无 `~/` |
| 默认白名单 | `SettingsState` / `SettingsFormState` | `ls`, `cat`, `grep`, `find`, `pwd` | `Get-ChildItem`, `Get-Content`, `Select-String` 等 |

### 排除的差异点

- `GenerateCommitMessageAction.kt:196` `ProcessBuilder("git", "diff", ...)` — 直接调可执行文件，不走 shell，**跨平台无问题**
- `SessionStore` — 使用 IntelliJ `PathManager` API，**已跨平台**
- `ApiKeyStore` — 使用 IntelliJ `PasswordSafe` API，**已跨平台**
- 网络层、UI 层 — 无平台相关代码

---

## 架构设计

### 核心抽象：`ShellPlatform`

```
┌─────────────────────────────────────────────────┐
│ ChatService                                      │
│  runCommandToolDefinition()                      │
│  → ShellPlatform.current().toolDefinition()      │
│                                                  │
│  buildBaseSystemPrompt()                         │
│  → 追加 ShellPlatform.current().shellHint()      │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ ShellPlatform (sealed class)                     │
│  ├── Unix    → sh -c, BashTool 工具定义          │
│  └── Windows → powershell -Command, PS 工具定义  │
└──────────────┬──────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────┐
│ CommandExecutionService                          │
│  → ShellPlatform.current().buildProcess(cmd, dir)│
│  → ShellPlatform.current().extractBaseCommand()  │
│  → ShellPlatform.current().hasPathsOutside...()  │
└─────────────────────────────────────────────────┘
```

### 新增文件

**`src/main/kotlin/com/github/codeplangui/execution/ShellPlatform.kt`**

```kotlin
sealed class ShellPlatform {
    abstract fun buildProcess(command: String, workDir: File): ProcessBuilder
    abstract fun hasPathsOutsideWorkspace(command: String, basePath: String): Boolean
    abstract fun extractBaseCommand(command: String): String
    abstract fun toolDefinition(): ToolDefinition
    abstract fun shellHint(): String
    abstract fun defaultWhitelist(): MutableList<String>

    object Unix : ShellPlatform() {
        override fun buildProcess(command: String, workDir: File) =
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

        override fun toolDefinition(): ToolDefinition = /* name="run_command", bash 描述 */

        override fun shellHint() = "" // Unix 是默认，无需额外提示

        override fun defaultWhitelist() = mutableListOf(
            "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
            "git", "ls", "cat", "grep", "find", "echo", "pwd"
        )
    }

    object Windows : ShellPlatform() {
        override fun buildProcess(command: String, workDir: File) =
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

        override fun toolDefinition(): ToolDefinition = /* name="run_powershell", PowerShell 描述 */

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

### 修改文件清单

| 文件 | 改动 |
|------|------|
| `execution/CommandExecutionService.kt:21` | `ShellPlatform.current().buildProcess(command, File(basePath)).redirectErrorStream(false).start()` |
| `execution/CommandExecutionService.kt:71` | 同上 |
| `execution/CommandExecutionService.kt:136` | `ShellPlatform.current().extractBaseCommand(command)` |
| `execution/CommandExecutionService.kt:147–151` | `ShellPlatform.current().hasPathsOutsideWorkspace(command, basePath)` |
| `ChatService.kt` `runCommandToolDefinition()` | `ShellPlatform.current().toolDefinition()` |
| `ChatService.kt` `buildBaseSystemPrompt()` | 追加 `ShellPlatform.current().shellHint()` |
| `settings/SettingsState.kt` `commandWhitelist` 默认值 | `ShellPlatform.current().defaultWhitelist()` |
| `settings/SettingsFormState.kt` `commandWhitelist` 默认值 | `ShellPlatform.current().defaultWhitelist()` |

---

## 错误处理

- `ShellPlatform.current()` 纯计算，不抛异常
- `buildProcess()` 只组装 `ProcessBuilder`，不启动进程
- 所有执行失败路径由现有 `ExecutionResult` sealed class 覆盖，无新增错误路径

---

## 测试策略

- `ShellPlatform.Unix` / `ShellPlatform.Windows` 是 `object`，可在任意平台显式调用测试
- `extractBaseCommand` 和 `hasPathsOutsideWorkspace` 两个实现各自写纯函数单元测试
- `buildProcess` 不单独测试（仅参数组装）
- 现有 `executeAsyncWithStream` 集成测试只在 Unix CI 跑，Windows 执行路径靠 `ShellPlatform.Windows` 单元测试覆盖

---

## 不在本次范围内

- WSL / Git Bash 检测与 fallback
- Windows 上的 sandbox / 安全沙箱
- 命令语法翻译（Unix → PowerShell 自动转换）
