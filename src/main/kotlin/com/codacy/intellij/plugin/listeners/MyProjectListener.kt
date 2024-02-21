package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.services.git.RepositoryManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.*

class MyStartupActivity : StartupActivity {
    @OptIn(DelicateCoroutinesApi::class)
    override fun runActivity(project: Project) {
        val gitRepository: GitRepository? = GitProvider.getRepository(project)
        val repositoryManager = project.service<RepositoryManager>()
        if (gitRepository != null && repositoryManager.currentRepository != gitRepository)
            GlobalScope.launch { repositoryManager.open(gitRepository) }
        GlobalScope.launch {
            Api().listTools()
        }

        val connection = project.messageBus.connect()
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
            if (repository.currentBranch != null) {
                CoroutineScope(Dispatchers.Default).launch {
                    repositoryManager.handleStateChange()
                }
            } else {
                repositoryManager.notifyDidChangeConfig()
            }
        })
    }
}