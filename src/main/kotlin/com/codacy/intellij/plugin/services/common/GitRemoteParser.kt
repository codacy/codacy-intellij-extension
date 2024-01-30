package com.codacy.intellij.plugin.services.common

import java.util.regex.Pattern

object GitRemoteParser {
    private val validProviders: Map<String, String> = mapOf(
        "github" to "gh",
        "gitlab" to "gl",
        "bitbucket" to "bb"
    )

    data class GitRemoteInfo(
        val provider: String,
        val organization: String,
        val repository: String
    )

    fun parseGitRemote(remoteUrl: String): GitRemoteInfo {
        val pattern = Pattern.compile("^.*(github|gitlab|bitbucket)\\.(?:com|org)[:|/](.+?)/(.+?)\\.git$")
        val matcher = pattern.matcher(remoteUrl)

        if (!matcher.matches()) {
            throw IllegalArgumentException("Invalid remote URL: $remoteUrl")
        }

        val providerName = matcher.group(1)
        val organization = matcher.group(2)
        val repository = matcher.group(3)

        val provider = validProviders[providerName] ?: throw IllegalArgumentException("Invalid provider: $providerName")

        return GitRemoteInfo(provider, organization, repository)
    }
}
