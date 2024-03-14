package com.codacy.intellij.plugin.services.api.models

data class GetPullRequestCoverageReportsResponse(
    val data: PullRequestCoverageReportsData
)

data class PullRequestCoverageReportsData(
    val headCommit: CommitCoverageReport,
    val commonAncestorCommit: CommitCoverageReport,
    val origin: CommitCoverageReport,
    val target: CommitCoverageReport
)

data class CommitCoverageReport(
    val commitId: Int,
    val commitSha: String,
    val reports: List<CoverageReport>
)

data class CoverageReport(
    val targetCommitSha: String,
    val commit: CommitInfo,
    val language: String,
    val createdAt: String,
    val status: String
)