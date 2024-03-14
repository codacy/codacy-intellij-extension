package com.codacy.plugin.services.api.models

data class ListToolsResponse(
    val data: List<ToolDetails>,
    val pagination: Pagination
)

data class ToolDetails(
    val uuid: String,
    val name: String,
    val version: String,
    val shortName: String,
    val documentationUrl: String,
    val sourceCodeUrl: String,
    val prefix: String,
    val needsCompilation: Boolean,
    val configurationFilenames: List<String>,
    val description: String,
    val dockerImage: String,
    val languages: List<String>,
    val clientSide: Boolean,
    val standalone: Boolean,
    val enabledByDefault: Boolean,
    val configurable: Boolean
)
