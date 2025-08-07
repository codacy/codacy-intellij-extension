package com.codacy.intellij.plugin.services.mcp.model

data class McpServer(val command: String, val args: List<String>, val env: Map<String, String>?)


data class McpConfig(val mcpServers: Map<String, McpServer>)
