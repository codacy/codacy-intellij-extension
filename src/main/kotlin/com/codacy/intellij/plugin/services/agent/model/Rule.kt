package com.codacy.intellij.plugin.services.agent.model

enum class Provider {
    GITHUB, GITLAB, BITBUCKET;

    override fun toString(): String {
        return when (this) {
            GITHUB -> "gh"
            GITLAB -> "gl"
            BITBUCKET -> "bb"
        }
    }

    fun fromString(s: String): Provider = when (s) {
        "github", "gh" -> GITHUB
        "bitbucket", "bb" -> BITBUCKET
        "gitlab", "gl" -> GITLAB
        else -> throw IllegalArgumentException("Unknown provider")
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

