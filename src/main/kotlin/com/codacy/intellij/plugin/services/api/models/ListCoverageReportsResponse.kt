package com.codacy.plugin.services.api.models

data class ListCoverageReportsResponse(
    val data: CoverageReportsData
)

data class CoverageReportsData(
    val hasCoverageOverview: Boolean,
    val lastReports: List<ReportDetails>
)

data class ReportDetails(
    val targetCommitSha: String,
    val commit: CommitInfo,
    val language: String,
    val createdAt: String,
    val status: String
)