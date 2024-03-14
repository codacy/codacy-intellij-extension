package com.codacy.plugin.services.api.models

data class ListPullRequestFilesResponse(
    val pagination: Pagination,
    val data: List<FileDetails>
)

data class FileDetails(
    val file: FileData,
    val coverage: FileCoverage,
    val quality: FileQuality,
    val comparedWithCommit: ComparedCommit,
    val uri: String
)

data class FileData(
    val commitId: Int,
    val commitSha: String,
    val fileId: Long,
    val fileDataId: Long,
    val path: String,
    val language: String,
    val gitProviderUrl: String
)

data class FileCoverage(
    val deltaCoverage: Double,
    val totalCoverage: Double
)

data class FileQuality(
    val deltaNewIssues: Int,
    val deltaFixedIssues: Int,
    val deltaComplexity: Int,
    val deltaClonesCount: Int
)

data class ComparedCommit(
    val commitId: Int,
    val coverage: CommitCoverage
)

data class CommitCoverage(
    val totalCoverage: Double
)