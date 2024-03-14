package com.codacy.intellij.plugin.services.api.models

data class ListRepositoryPullRequestsResponse(
    val data: List<GetRepositoryPullRequestResponse>,
    val pagination: Pagination
)
