package com.codacy.plugin.services.api.models

data class GetPatternResponse (
    val data: Pattern,
    val pagination: Pagination
)
data class Pattern (
    val id: String,
    val title: String,
    val category: String,
    val subCategory: String,
    val level: String,
    val severityLevel: String,
    val description: String,
    val explanation: String?,
    val enabled: Boolean,
    val languages: List<String>,
    val timeToFix: Int,
    val parameters: List<Parameter>
)
data class Parameter(
    val name: String,
    val description: String,
    val default: String
)
