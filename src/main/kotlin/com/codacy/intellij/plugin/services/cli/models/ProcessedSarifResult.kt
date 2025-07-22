package com.codacy.intellij.plugin.services.cli.models

data class ProcessedSarifResult(
    val tool: String,
    val rule: RuleInfo? = null,
    val level: String,
    val message: String,
    val filePath: String?,
    val region: Region?
)

data class RuleInfo(
    val id: String,
    val name: String?,
    val helpUri: String?,
    val shortDescription: String?
)

data class Region(
    val startLine: Int? = null,
    val startColumn: Int? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null
)
