package com.codacy.intellij.plugin.services.mcp.model

import com.codacy.intellij.plugin.services.common.Config
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists

//TODO this might already exist somewhere else
enum class Provider {
    GITHUB, GITLAB, BITBUCKET;

    override fun toString(): String {
        return when (this) {
            GITHUB -> "gh"
            GITLAB -> "gl"
            BITBUCKET -> "bb"
        }
    }
}

enum class RuleScope {
    GUARDRAILS, GENERAL
}

data class Rule(
    val `when`: String? = null,
    val enforce: List<String>,
    val scope: RuleScope
)

data class RuleConfig(
    val name: String,
    val description: String,
    val rules: List<Rule>
)

data class RepositoryParams(
    val provider: Provider,
    val organization: String,
    val repository: String,
)

