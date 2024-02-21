package com.codacy.intellij.plugin.services.api.models

data class GetRepositoryPullRequestResponse(
    val isUpToStandards: Boolean?,
    val isAnalysing: Boolean,
    val pullRequest: PullRequestData,
    val newIssues: Int,
    val fixedIssues: Int,
    val deltaComplexity: Int,
    val deltaClonesCount: Int,
    val deltaCoverageWithDecimals: Double,
    val deltaCoverage: Int,
    val diffCoverage: Int,
    val coverage: CoverageDetails?,
    val quality: QualityDetails,
    val meta: MetaInfo
)

data class PullRequestData(
    val id: Int,
    val number: Int,
    val updated: String,
    val status: String,
    val repository: String,
    val title: String,
    val owner: Owner,
    val originBranch: String,
    val targetBranch: String,
    val gitHref: String
)

data class Owner(
    val name: String,
    val avatarUrl: String,
    val username: String,
    val email: String
)

data class CoverageDetails(
    val deltaCoverage: Double?,
    val diffCoverage: DiffCoverage?,
    val isUpToStandards: Boolean,
    val resultReasons: List<ResultReason>
)

data class DiffCoverage(
    val value: Int,
    val coveredLines: Int,
    val coverableLines: Int,
    val cause: String
)

data class ResultReason(
    val gate: String,
    val expected: Int,
    val expectedThreshold: ExpectedThreshold,
    val isUpToStandards: Boolean
)

data class ExpectedThreshold(
    val threshold: Int,
    val minimumSeverity: String
)

data class QualityDetails(
    val newIssues: Int,
    val fixedIssues: Int,
    val deltaComplexity: Int,
    val deltaClonesCount: Int,
    val isUpToStandards: Boolean,
    val resultReasons: List<ResultReason>
)

data class MetaInfo(
    val analyzable: Boolean,
    val reason: String
)
