package com.codacy.intellij.plugin.services.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import com.intellij.openapi.project.ProjectManager

@Service
class GitProvider{

    companion object {

        fun getRepository(): GitRepository? {
            val currentProject: Project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
            return GitUtil.getRepositoryManager(currentProject).repositories.firstOrNull()
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
