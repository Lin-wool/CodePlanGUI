package com.github.codeplangui.execution

import java.io.File

sealed class ShellPlatform {

    abstract fun buildProcess(command: String, workDir: File): ProcessBuilder
    abstract fun extractBaseCommand(command: String): String
    abstract fun hasPathsOutsideWorkspace(command: String, basePath: String): Boolean
    abstract fun toolName(): String
    abstract fun shellHint(): String
    abstract fun defaultWhitelist(): List<String>

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

        override fun defaultWhitelist(): List<String> = mutableListOf(
            "cargo", "gradle", "mvn", "npm", "yarn", "pnpm",
            "git", "ls", "cat", "grep", "find", "echo", "pwd"
        )
    }

    object Windows : ShellPlatform() {

        override fun buildProcess(command: String, workDir: File): ProcessBuilder =
            ProcessBuilder("powershell", "-NoProfile", "-Command", command).directory(workDir)

        override fun extractBaseCommand(command: String): String {
            val trimmed = command.trimStart()
            // Detect absolute paths: Windows drive (C:\...), UNC (\\...), or Unix-style (/c/...)
            val isAbsolutePath = trimmed.matches(Regex("^(?:[A-Za-z]:\\\\|\\\\\\\\|/[a-zA-Z]/).*"))
            if (isAbsolutePath) {
                // Normalize to backslashes, find last path separator, extract exe name
                val normalized = trimmed.replace('/', '\\')
                val lastSep = normalized.lastIndexOfAny(charArrayOf('\\'))
                val nameWithArgs = if (lastSep >= 0) normalized.substring(lastSep + 1) else normalized
                return nameWithArgs.split(" ").first().removeSuffix(".exe")
            }
            // Plain cmdlet or bare command: take first token before shell operators
            val base = trimmed.split(" ", "|", ";", ">", "<", "&").first().trim()
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

        override fun defaultWhitelist(): List<String> = mutableListOf(
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
