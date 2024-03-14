package com.codacy.plugin.services.api.models

data class ListRepositoryPullRequestsResponse(
    val data: List<GetRepositoryPullRequestResponse>,
    val pagination: Pagination
)
