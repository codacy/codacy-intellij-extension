package com.codacy.intellij.plugin.services.api.models

data class ListPullRequestIssuesResponse(
    val analyzed: Boolean,
    val data: List<IssueDetails>,
    val pagination: Pagination
)

data class IssueDetails(
    val commitIssue: CommitIssue,
    val deltaType: String,
    val uri: String
)

data class CommitIssue(
    val issueId: String,
    val resultDataId: Long,
    val filePath: String,
    val fileId: Long,
    val patternInfo: PatternInfo,
    val toolInfo: ToolInfo,
    val lineNumber: Int,
    val message: String,
    var suggestion: String?,
    val language: String,
    val lineText: String,
    val commitInfo: IssueCommitInfo
)

data class PatternInfo(
    val id: String,
    val title: String,
    val category: String,
    val subCategory: String,
    val level: String,
    val severityLevel: String
)

data class ToolInfo(
    val uuid: String,
    val name: String
)

data class IssueCommitInfo(
    val sha: String,
    val commiter: String,
    val commiterName: String,
    val timestamp: String
)