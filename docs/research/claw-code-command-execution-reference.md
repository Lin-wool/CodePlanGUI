# claw-code 命令执行与权限模型参考

> 调研来源：`~/SourceLib/fishNotExist/claw-code`（Rust CLI agent harness）
> 调研目的：为 CodePlanGUI 设计 AI 自主命令执行 + 安全机制提供参考实现

---

## 1. 整体架构

```
用户会话 (PermissionMode)
  └── PermissionPolicy
        ├── tool_requirements（每个工具所需的最低 mode）
        ├── allow_rules（白名单规则 → 自动放行）
        ├── deny_rules（黑名单规则 → 自动拒绝）
        └── ask_rules（必须弹框规则 → 即使 mode 够也要问）

AI 发起 tool_call
  └── execute_tool_with_enforcer()
        ├── classify_bash_permission(command)   ← 动态分级
        ├── enforcer.check_with_required_mode() ← 权限门控
        └── 通过 → run_bash() 执行，结果回传 AI
```

---

## 2. PermissionMode 枚举

```rust
pub enum PermissionMode {
    ReadOnly,          // 只读，不可写
    WorkspaceWrite,    // 可在工作区写文件
    DangerFullAccess,  // 任意命令执行
    Prompt,            // 每次都弹确认框
    Allow,             // 始终放行（最高信任）
}
```

会话启动时设置一个全局 mode，工具执行时与 `required_mode` 对比。

---

## 3. 授权流程（核心逻辑）

`PermissionPolicy::authorize(tool_name, input, prompter)` 的判定顺序：

1. **Hook Override 优先**：外部 hook 可以强制 Allow / Deny / Ask
2. **Deny rules 优先拦截**：命中黑名单 → 立即拒绝，不看其他规则
3. **Allow rules 白名单放行**：命中白名单 → 放行（但 ask_rules 仍可覆盖）
4. **Ask rules 强制弹框**：命中 ask 规则 → 无论 mode 是否够用，都弹确认框
5. **Mode 比较**：`current_mode >= required_mode` → 放行
6. **需要升级 mode 时**：如果是 `WorkspaceWrite → DangerFullAccess`，弹确认框
7. **兜底**：拒绝，并附上具体原因

---

## 4. 规则格式

规则格式：`tool_name(prefix_pattern:*)` 

```
bash(git:*)       # 匹配所有以 "git" 开头的 bash 命令
bash(rm -rf:*)    # 匹配所有以 "rm -rf" 开头的 bash 命令
```

三类规则可在运行时配置：

| 规则类型   | 行为                                   |
|-----------|---------------------------------------|
| allow_rules | 白名单，命中直接放行                    |
| deny_rules  | 黑名单，命中直接拒绝                    |
| ask_rules   | 即使 mode 够用也强制弹框确认            |

---

## 5. Bash 命令动态分级（classify_bash_permission）

不是所有 bash 命令都需要 `DangerFullAccess`，claw-code 做了动态分级：

**只读命令白名单**（降级到 WorkspaceWrite）：
```
cat, head, tail, less, more, ls, ll, dir, find, test,
grep, rg, awk, sed, file, stat, readlink, wc, sort,
uniq, cut, tr, pwd, echo, printf
```

**降级条件**：
1. 基础命令在白名单内 **且**
2. 没有访问工作区外的绝对路径（`/` 开头但不在 CWD 内）
3. 没有 `../../` 路径穿越

否则一律 `DangerFullAccess`。

**实现要点**：
- 提取 `|`、`;`、`>`、`<` 之前的基础命令名
- 路径检查对比 `std::env::current_dir()`

---

## 6. 工具权限等级注册

```rust
ToolSpec { name: "bash",       required_permission: DangerFullAccess }  // 动态覆盖
ToolSpec { name: "read_file",  required_permission: ReadOnly }
ToolSpec { name: "write_file", required_permission: WorkspaceWrite }
ToolSpec { name: "edit_file",  required_permission: WorkspaceWrite }
ToolSpec { name: "glob_search",required_permission: ReadOnly }
ToolSpec { name: "grep_search",required_permission: ReadOnly }
```

---

## 7. 信任根（trusted_roots）白名单

对于 agent worker 的启动信任问题，claw-code 有 `trusted_roots`：

- 在 `settings.json` 中配置 `trusted_roots = ["/your/repo/path"]`
- Worker 创建时，配置级白名单与调用级白名单合并
- 命中白名单的仓库自动跳过 trust 弹框
- 未命中的仓库进入 `TrustRequired` 状态，需要手动确认

---

## 8. PermissionRequest 弹框数据结构

当需要弹出确认框时，传给 prompter 的数据：

```rust
pub struct PermissionRequest {
    pub tool_name: String,       // 工具名，如 "bash"
    pub input: String,           // 命令内容，如 "rm -rf ./dist"
    pub current_mode: PermissionMode,  // 当前会话 mode
    pub required_mode: PermissionMode, // 该命令所需 mode
    pub reason: Option<String>,        // 说明为什么需要弹框
}
```

Prompter 返回 `Allow` 或 `Deny { reason }`。

---

## 9. 对 CodePlanGUI 的设计启发

| claw-code 概念 | CodePlanGUI 对应设计 |
|---------------|---------------------|
| `PermissionMode` 枚举 | 用户在 Settings 中设置的"允许级别"（只读 / 项目内 / 全部） |
| `allow_rules` 白名单 | 用户配置的"允许执行"命令前缀列表 |
| `deny_rules` 黑名单 | 系统内置的永久拒绝列表（`rm -rf /`、`DROP TABLE` 等） |
| `ask_rules` 强制弹框 | 敏感但不完全禁止的命令，每次都弹审批框 |
| `classify_bash_permission` | AI 调用前，Kotlin 后端对命令做动态分级 |
| `PermissionRequest` | JCEF Bridge 向 Vue 推送的审批弹框 payload |
| `trusted_roots` | 白名单命令列表（用户在 Settings 中配置） |
| `PermissionPrompter::decide()` | Vue 弹框的用户点击 Allow/Deny → 通过 Bridge 回调 Kotlin |

### 关键差异

claw-code 是 CLI + TUI，弹框是终端交互；CodePlanGUI 的弹框需要通过 JCEF Bridge 从 Vue 发起，等待用户操作后异步回调 Kotlin，再继续 AI 对话——需要设计一个**异步审批挂起机制**。

---

## 10. 参考文件

| 文件 | 内容 |
|-----|------|
| `rust/crates/runtime/src/permissions.rs` | PermissionMode、PermissionPolicy、PermissionRule 完整实现 |
| `rust/crates/tools/src/lib.rs` | mvp_tool_specs、classify_bash_permission、execute_tool_with_enforcer |
| `rust/USAGE.md` | `--permission-mode`、`--allowedTools` CLI 用法 |
| `rust/ROADMAP.md` | PowerShell/bash 动态分级 gap 的详细描述（item #50） |
