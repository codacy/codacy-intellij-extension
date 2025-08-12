package com.codacy.intellij.plugin.services.mcp.model

interface McpConfig

interface McpServer

data class McpServerJunie(
    val command: String,
    val args: List<String>,
    val env: Map<String, String>?
): McpServer


data class McpConfigJunie(val mcpServers: Map<String, McpServerJunie>): McpConfig


data class McpServerGithubCopilot(
    val type: String = "stdio",
    val command: String,
    val args: List<String>,
    val env: Map<String, String>?
): McpServer

data class McpConfigGithubCopilot(val servers: Map<String, McpServerGithubCopilot>): McpConfig
