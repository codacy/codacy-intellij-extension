package com.codacy.intellij.plugin.services.mcp

import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

interface AiAgent {

    val configurationPath: Path
    val configurationFilePath: Path

    fun getAiAgentSuggestionFilePath(project: Project): Path

    data object JUNIE : AiAgent {
        //TODO maybe different way
        private val homePath = System.getProperty("user.home")


        override val configurationPath: Path = Paths.get(homePath, ".junie", "mcp")
        override val configurationFilePath: Path = Paths.get(configurationPath.toString(), "mcp.json")

        override fun getAiAgentSuggestionFilePath(project: Project): Path {
            val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")

            return Paths.get(basePath, ".junie", "guidelines.md")
        }


        override fun toString() = "Junie Agent"
    }

    //TODO not working on it yet
    data object GITHUB_COPILOT : AiAgent {

        override fun toString() = "GitHub Copilot Agent"
        override val configurationPath: Path = TODO("not implemented yet")
        override val configurationFilePath: Path = TODO("not implemented yet")

        override fun getAiAgentSuggestionFilePath(project: Project): Path {
            TODO("Not yet implemented")
        }
    }
}
