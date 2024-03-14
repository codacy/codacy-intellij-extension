package com.codacy.plugin.services.api.models

data class Pagination(
    val cursor: String?,
    val limit: Int,
    val total: Int
)

data class CommitInfo(
    val sha: String,
    val id: Int,
    val commitTimestamp: String,
    val authorName: String,
    val authorEmail: String,
    val message: String,
    val startedAnalysis: String,
    val endedAnalysis: String,
    val isMergeCommit: Boolean,
    val gitHref: String,
    val parents: List<String>,
    val branches: List<Branch>
)

data class Problem(
    val message: String,
    val actions: List<Action>,
    val code: String,
    val severity: String
)

data class Action(
    val name: String,
    val url: String
)

data class Branch(
    val id: Int,
    val name: String,
    val isDefault: Boolean,
    val isEnabled: Boolean,
    val lastUpdated: String,
    val branchType: String,
    val lastCommit: String
)

data class Badges(
    val grade: String,
    val coverage: String
)

data class RepositoryData(
    val repositoryId: Int,
    val provider: String,
    val owner: String,
    val name: String,
    val fullPath: String,
    val visibility: String,
    val remoteIdentifier: String,
    val lastUpdated: String,
    val permission: String,
    val problems: List<Problem>,
    val languages: List<String>,
    val defaultBranch: Branch,
    val badges: Badges,
    val codingStandardId: Int,
    val codingStandardName: String,
    val addedState: String,
    val gatePolicyId: Int,
    val gatePolicyName: String
)