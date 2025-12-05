package com.codacy.intellij.plugin.services.api

import com.codacy.intellij.plugin.services.api.models.*
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Logger
import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

@Service
class Api {

    private val config = service<Config>()

    private val baseUrl: String
        get() = config.baseUri

    private val apiToken: String?
        get() = config.storedApiToken

   private var tools: List<ToolDetails>? = null

    fun getTool(toolUuid: String): ToolDetails? {
        return tools?.find { it.uuid == toolUuid }
    }

    private suspend fun <T> makeRequest(endpointUrl: String, responseClass: Class<T>): T {
        return withContext(Dispatchers.IO) {
            try {
                config.init()
                val url = URL("$baseUrl/$endpointUrl")
                println("Request URL: $url")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("api-token", apiToken)

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { `in` ->
                        val response = `in`.readText()
                        Gson().fromJson(response, responseClass)
                    }
                } else {
                    Logger.error("Failed to fetch data: HTTP response code $responseCode")
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                        config.storeApiToken("")
                    throw Exception("Failed to fetch data: HTTP response code $responseCode")
                }
            }
            catch (e: Exception) {
                Logger.error("Failed to fetch data: ${e.message}")
                responseClass.newInstance()
            }
        }
    }

    suspend fun getRepositoryWithAnalysis(provider: String, remoteOrganizationName: String, repositoryName: String): GetRepositoryWithAnalysisResponse {
        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName"
        return makeRequest(endpointUrl, GetRepositoryWithAnalysisResponse::class.java)
    }

    suspend fun getRepositoryCoverageStatus(provider: String, remoteOrganizationName: String, repositoryName: String): ListCoverageReportsResponse {
        val endpointUrl = "organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/coverage/status"
        return makeRequest(endpointUrl, ListCoverageReportsResponse::class.java)
    }

    suspend fun getPullRequestQualitySettings(provider: String, remoteOrganizationName: String, repositoryName: String): GetPullRequestQualitySettingsResponse {
        val endpointUrl = "organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/settings/quality/pull-requests"
        return makeRequest(endpointUrl, GetPullRequestQualitySettingsResponse::class.java)
    }

    suspend fun listRepositoryPullRequests(provider: String, remoteOrganizationName: String, repositoryName: String): ListRepositoryPullRequestsResponse {
        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/pull-requests"
        return makeRequest(endpointUrl, ListRepositoryPullRequestsResponse::class.java)
    }

    suspend fun getRepositoryPullRequest(provider: String, remoteOrganizationName: String, repositoryName: String, pullRequestNumber: Int): GetRepositoryPullRequestResponse {
        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/pull-requests/$pullRequestNumber"
        return makeRequest(endpointUrl, GetRepositoryPullRequestResponse::class.java)
    }

    suspend fun listPullRequestFiles(provider: String, remoteOrganizationName: String, repositoryName: String, pullRequestNumber: Int, cursor: String): ListPullRequestFilesResponse {
        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/pull-requests/$pullRequestNumber/files?limit=100&cursor=$cursor"
        return makeRequest(endpointUrl, ListPullRequestFilesResponse::class.java)
    }

    suspend fun listPullRequestIssues(provider: String, remoteOrganizationName: String, repositoryName: String, pullRequestNumber: Int): ListPullRequestIssuesResponse {
        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/pull-requests/$pullRequestNumber/issues"
        return makeRequest(endpointUrl, ListPullRequestIssuesResponse::class.java)
    }

//    suspend fun listCoverageReports(provider: String, remoteOrganizationName: String, repositoryName: String): ListCoverageReportsResponse {
//        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/coverage/status"
//        return makeRequest(endpointUrl, ListCoverageReportsResponse::class.java)
//    }

    suspend fun getPullRequestCoverageReports(provider: String, remoteOrganizationName: String, repositoryName: String, pullRequestNumber: Int): GetPullRequestCoverageReportsResponse {
        val endpointUrl = "analysis/organizations/$provider/$remoteOrganizationName/repositories/$repositoryName/pull-requests/$pullRequestNumber/coverage/status"
        return makeRequest(endpointUrl, GetPullRequestCoverageReportsResponse::class.java)
    }

    suspend fun listTools() {
        val endpointUrl = "tools"
        tools = makeRequest(endpointUrl, ListToolsResponse::class.java).data
    }

    suspend fun getPattern(toolUuid: String, patternId: String): GetPatternResponse {
        val endpointUrl = "tools/$toolUuid/patterns/$patternId"
        return makeRequest(endpointUrl, GetPatternResponse::class.java)
    }

    suspend fun getRepository(provider: String, remoteOrganizationName: String, repositoryName: String): GetRepositoryResponse {
        val endpointUrl = "organizations/$provider/$remoteOrganizationName/repositories/$repositoryName"
        return makeRequest(endpointUrl, GetRepositoryResponse::class.java)
    }

    suspend fun listRepositoryBranches(provider: String, remoteOrganizationName: String, repositoryName: String, enabledOnly: Boolean = true): ListRepositoryBranchesResponse {
        val enabledParam = if (enabledOnly) "?enabled=true" else ""
        val endpointUrl = "repositories/$provider/$remoteOrganizationName/$repositoryName/branches$enabledParam"
        return makeRequest(endpointUrl, ListRepositoryBranchesResponse::class.java)
    }

    suspend fun getUserProfile(): UserProfile? {
        val endpointUrl = "user"
        return try {
            makeRequest(endpointUrl, GetUserProfileResponse::class.java).data
        } catch (e: Exception) {
            Logger.error("Failed to fetch user profile: ${e.message}")
            null
        }
    }

    data class GetUserProfileResponse(
        val data: UserProfile
    )
}
