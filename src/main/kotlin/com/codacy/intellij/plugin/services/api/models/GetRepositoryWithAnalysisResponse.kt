package com.codacy.intellij.plugin.services.api.models

data class GetRepositoryWithAnalysisResponse(
    val data: RepositoryWithAnalysisData
)

data class RepositoryWithAnalysisData(
    val lastAnalysedCommit: CommitInfo,
    val grade: Int,
    val gradeLetter: String,
    val issuesPercentage: Int,
    val complexFilesPercentage: Int,
    val duplicationPercentage: Int,
    val repository: RepositoryData,
    val branch: Branch,
    val selectedBranch: Branch,
    val coverage: Coverage
)

data class Coverage(
    val filesUncovered: Int,
    val filesWithLowCoverage: Int,
    val coveragePercentage: Int,
    val coveragePercentageWithDecimals: Double,
    val numberTotalFiles: Int,
    val numberCoveredLines: Int,
    val numberCoverableLines: Int
)
