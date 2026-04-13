# AI 命令执行设计文档

**日期：** 2026-04-13  
**功能：** AI 自主命令执行 + 白名单安全机制  
**阶段：** Phase 3 — Safe Action Surfaces（提前实现）  

---

## 问题陈述

CodePlanGUI 当前的 Chat 只能回答问题，无法执行操作。用户需要 AI 在推理过程中自主执行命令（如跑测试、查文件列表、执行构建），执行结果回传给 AI 继续推理。核心挑战是在保持安全的前提下实现这一能力。

---

## 用户场景

- 用户说"帮我跑一下测试"，AI 自主调用 `cargo test --workspace`，读取结果后分析失败原因
- AI 在推理过程中发现需要查看目录结构，自主调用 `ls src/`，再根据结果给出建议
- 用户问"构建为什么失败"，AI 调用 `gradle build`，读取 stderr 后定位错误

每次执行前，用户会看到审批弹框，可以选择允许或拒绝。未在白名单内的命令直接拦截，不弹框。

---

## 方案选型

选用 **OpenAI Function Calling（tool_use 协议）**：

- 所有主流 OpenAI 兼容 API 均支持（GPT-4、Claude、Qwen、DeepSeek）
- 执行意图由协议层保证，不依赖 prompt 稳定性
- AI 能读到 `tool_result` 继续推理，完整闭环
- 排除方案 B（Prompt 标记解析，脆弱）和方案 C（MCP server，过度工程化，留作 Phase 4）

---

## 架构

### 新增组件

| 组件 | 层 | 职责 |
|------|----|------|
| `CommandExecutionService` | Kotlin | 白名单校验、ProcessBuilder 执行、超时控制、输出采集 |
| `CommandExecutionSettings` | Kotlin | 白名单列表和超时配置的持久化（PersistentStateComponent） |
| `ApprovalDialog` | Vue | 展示命令内容、接收用户 Allow/Deny 决定 |
| ChatService tool_call 状态机 | Kotlin | 在现有 SSE 解析基础上识别 tool_call delta |

### 组件关系

```
Vue Webview
  ├── Chat 消息流（含执行结果卡片）
  └── ApprovalDialog（弹框）
        ↕ JCEF Bridge (postMessage，扩展新事件类型)
Kotlin Plugin
  ├── ChatService（SSE 状态机扩展）
  ├── CommandExecutionService（执行层）
  └── CommandExecutionSettings（白名单持久化）
        ↕ OkHttp SSE
  OpenAI Compatible API（携带 tools 参数）
```

---

## 数据流

### 1. 请求构建

`ChatService.sendMessage()` 在现有 messages 基础上附加 `tools` 参数：

```json
{
  "tools": [{
    "type": "function",
    "function": {
      "name": "run_command",
      "description": "Execute a shell command in the project root directory. Only use when you need to inspect state or run a task that the user has asked for.",
      "parameters": {
        "type": "object",
        "properties": {
          "command": { "type": "string", "description": "The shell command to execute" },
          "description": { "type": "string", "description": "One-line explanation of why you are running this command" }
        },
        "required": ["command", "description"]
      }
    }
  }]
}
```

功能开关关闭时，不附加 `tools` 参数，行为与现在完全一致。

### 2. SSE 状态机

现有状态机只处理 text delta，新增 tool_call 分支：

```
STREAMING_TEXT
  ├── delta.content 有内容       → 追加渲染文本
  ├── delta.tool_calls 出现      → 切换 ACCUMULATING_TOOL_CALL
  │     记录 tool_call_id、function_name
  │     初始化 arguments_buffer = ""
  └── finish_reason == "stop"   → DONE

ACCUMULATING_TOOL_CALL
  ├── delta.tool_calls[].function.arguments 到达 → arguments_buffer += chunk
  └── finish_reason == "tool_calls"
        → JSON.parse(arguments_buffer)
        → 提取 command、description
        → 调用 CommandExecutionService.requestExecution()
        → 切换 WAITING_RESULT

WAITING_RESULT
  └── ExecutionResult 返回
        → 构建 tool_result 消息
        → 发起第二轮 API 请求
        → 切换 STREAMING_TEXT（AI 继续生成）
```

### 3. 权限校验与执行

```kotlin
suspend fun requestExecution(command: String, description: String): ExecutionResult {
    // 1. 功能开关
    if (!settings.enabled) return ExecutionResult.blocked("Command execution is disabled")

    // 2. 白名单校验（前缀匹配 base command）
    val baseCmd = extractBaseCommand(command)  // 提取第一个词，去掉路径前缀
    if (!settings.whitelist.any { baseCmd == it || baseCmd.startsWith("$it ") }) {
        return ExecutionResult.blocked("'$baseCmd' is not in the allowed command list")
    }

    // 3. 路径安全检查（工作区外的绝对路径 → 拒绝）
    if (hasPathsOutsideWorkspace(command, project.basePath!!)) {
        return ExecutionResult.blocked("Command accesses paths outside the project")
    }

    // 4. 弹框审批（协程挂起等待用户操作）
    val approved = requestApproval(command, description)
    if (!approved) return ExecutionResult.denied("User rejected the command")

    // 5. 执行
    return executeAsync(command)
}
```

### 4. 审批 Bridge 挂起机制

```kotlin
// Kotlin 侧：挂起等待 Vue 回调
private val pendingApprovals = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

suspend fun requestApproval(command: String, description: String): Boolean {
    val requestId = UUID.randomUUID().toString()
    val future = CompletableFuture<Boolean>()
    pendingApprovals[requestId] = future

    bridge.postMessage(mapOf(
        "type" to "approval_request",
        "requestId" to requestId,
        "command" to command,
        "description" to description
    ))

    return withContext(Dispatchers.IO) {
        try {
            future.get(60, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            false  // 60s 无操作视为拒绝
        } finally {
            pendingApprovals.remove(requestId)
        }
    }
}

// Bridge 消息处理器中调用
fun onApprovalResponse(requestId: String, decision: String) {
    pendingApprovals[requestId]?.complete(decision == "allow")
}
```

### 5. 命令执行（ProcessBuilder）

```kotlin
suspend fun executeAsync(command: String): ExecutionResult = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    val process = ProcessBuilder("sh", "-c", command)
        .directory(File(project.basePath!!))
        .redirectErrorStream(false)
        .start()

    val finished = process.waitFor(settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)

    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()

    if (!finished) {
        process.destroyForcibly()
        ExecutionResult(
            command = command,
            stdout = stdout.truncate(4000),
            stderr = "Process killed: exceeded ${settings.timeoutSeconds}s timeout",
            exitCode = -1,
            durationMs = System.currentTimeMillis() - startMs,
            truncated = stdout.length > 4000
        )
    } else {
        ExecutionResult(
            command = command,
            stdout = stdout.truncate(4000),
            stderr = stderr.truncate(2000),
            exitCode = process.exitValue(),
            durationMs = System.currentTimeMillis() - startMs,
            truncated = stdout.length > 4000 || stderr.length > 2000
        )
    }
}
```

### 6. tool_result 格式

```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "{\"status\":\"ok\",\"exit_code\":0,\"stdout\":\"running 12 tests...\\ntest result: ok. 12 passed\",\"stderr\":\"\",\"duration_ms\":4231}"
}
```

---

## Settings UI

### 持久化模型

```kotlin
@State(name = "CommandExecutionSettings", storages = [Storage("codeplangui.xml")])
class CommandExecutionSettings : PersistentStateComponent<CommandExecutionSettings.State> {
    data class State(
        var enabled: Boolean = false,
        var whitelist: MutableList<String> = mutableListOf(
            "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
            "git", "ls", "cat", "grep", "find", "echo", "pwd"
        ),
        var timeoutSeconds: Int = 30
    )
}
```

**默认关闭**（`enabled = false`）。用户必须主动在 Settings 中开启。

### 面板布局（在现有 Settings 面板底部新增区块）

```
Command Execution                           [Toggle: OFF/ON]
─────────────────────────────────────────────────────────
Allowed Commands (base command prefix matching)

  ┌──────────┬──────────────────────────────────────┐
  │ cargo    │                                 [✕] │
  │ git      │                                 [✕] │
  │ npm      │                                 [✕] │
  │ ls       │                                 [✕] │
  └──────────┴──────────────────────────────────────┘

  [ + Add ]

Execution timeout    [ 30 ] seconds

⚠ AI will still require your approval before each command runs.
  Commands not in this list are blocked without prompting.
```

---

## Vue UI

### Bridge 事件扩展

**Kotlin → Vue（审批请求）：**
```json
{ "type": "approval_request", "requestId": "...", "command": "...", "description": "..." }
```

**Vue → Kotlin（用户决定）：**
```json
{ "type": "approval_response", "requestId": "...", "decision": "allow" }
```

**Kotlin → Vue（状态更新，内嵌在消息流中）：**
```json
{ "type": "execution_status", "requestId": "...", "status": "running" | "done", "result": {...} }
```

### ApprovalDialog 组件

```
┌─────────────────────────────────────────┐
│  ⚠  AI 请求执行命令                      │
│                                         │
│  $ cargo test --workspace               │
│                                         │
│  Run all workspace tests                │
│                                         │
│  [    拒绝    ]        [   允许执行   ]  │
└─────────────────────────────────────────┘
```

### 结果卡片（5 种状态）

| 状态 | 图标 | 内容 |
|------|------|------|
| 等待审批 | 🔐 | `Waiting for approval · $ command` |
| 执行中 | ⟳ | `Running · $ command` |
| 成功 | ✓ | `Completed · exit 0 · 4.2s · $ command · [stdout 折叠]` |
| 失败 | ✗ | `Failed · exit 1 · 1.8s · $ command · [stderr 折叠]` |
| 被拦截 | ⊘ | `Blocked · reason · $ command` |

**输出折叠规则：**
- ≤ 10 行：直接展开
- > 10 行：显示前 5 行 + "▼ show N more lines"
- > 4000 字符：截断 + `[output truncated]`

---

## 验收标准

- 功能默认关闭，开启前无任何行为变化
- 不在白名单的命令直接返回 blocked，不弹框
- 在白名单内的命令弹出审批框，用户拒绝后命令不执行
- 执行超时后进程强制终止，结果卡片显示 timeout 状态
- AI 能读到 tool_result 继续推理生成最终回答
- 输出超过 4000 字符时自动截断，附注截断说明
- 60 秒无操作自动视为拒绝

---

## 开放问题

- **多工具调用**：若 AI 在一轮对话中连续发起多个 tool_call，当前设计串行处理；并发审批交互体验待定
- **输出流式展示**：当前方案等命令执行完才渲染结果，长时间命令无进度反馈；可在 v2 用 ProcessBuilder 的 inputStream 流式推送
- **命令历史**：执行过的命令是否要持久化展示，留作后续迭代

---

## 参考

- `docs/research/claw-code-command-execution-reference.md`：claw-code 权限模型调研
- `rust/crates/runtime/src/permissions.rs`：PermissionPolicy 参考实现
- `rust/crates/tools/src/lib.rs`：classify_bash_permission、mvp_tool_specs 参考实现
