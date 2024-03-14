package com.codacy.intellij.plugin.services.api.models

data class GetPullRequestQualitySettingsResponse(
    val data: QualitySettingsData
)

data class QualitySettingsData(
    val qualityGate: QualityGate,
    val repositoryGatePolicyInfo: RepositoryGatePolicyInfo
)

data class QualityGate(
    val issueThreshold: IssueThreshold,
    val securityIssueThreshold: Int,
    val duplicationThreshold: Int,
    val coverageThreshold: Int,
    val coverageThresholdWithDecimals: Double,
    val diffCoverageThreshold: Int,
    val complexityThreshold: Int
)

data class IssueThreshold(
    val threshold: Int,
    val minimumSeverity: String?
)

data class RepositoryGatePolicyInfo(
    val id: Int,
    val name: String
)
