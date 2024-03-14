package com.codacy.plugin.services.git

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

    }
}
