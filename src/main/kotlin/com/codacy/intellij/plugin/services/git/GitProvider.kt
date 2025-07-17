package com.codacy.intellij.plugin.services.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository

@Service
class GitProvider{

    companion object {

        fun getRepository(project: Project): GitRepository? {
            return GitUtil.getRepositoryManager(project).repositories.firstOrNull()
        }

        fun getHeadCommitSHA(project: Project): String? {
            return GitUtil.getRepositoryManager(project).repositories.firstOrNull()?.currentRevision
        }

        fun isHeadAhead(project: Project): Boolean {
            val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull() ?: return false
            val currentBranch = repository.currentBranch ?: return false
            val trackedBranch = GitBranchUtil.getTrackInfoForBranch(repository, currentBranch)?.remoteBranch ?: return false

            val headCommit = repository.branches.getHash(currentBranch)
            val trackedCommit = repository.branches.getHash(trackedBranch)

            return headCommit != null && trackedCommit != null && headCommit != trackedCommit
        }

        fun extractGitInfo(repository: GitRepository): Triple<String, String, String>? {
            val remote = repository.remotes.firstOrNull() ?: return null
            val url = remote.firstUrl ?: return null

            val regex = Regex(
                """(?:https://|git@)([^/:]+)[/:]([^/]+)/([^/.]+)(?:\.git)?"""
            )
            val match = regex.find(url) ?: return null

            val provider = match.groupValues[1]
            val orgOrUser = match.groupValues[2]
            val project = match.groupValues[3]
            return Triple(provider, orgOrUser, project)
        }

    }
}
