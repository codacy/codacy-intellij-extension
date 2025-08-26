package com.codacy.intellij.plugin.services.paths.behaviour

import com.codacy.intellij.plugin.services.paths.PathsBehaviour
import com.intellij.openapi.project.Project

class PathsUnix : PathsBehaviour{

    override fun rootPath(project: Project): String {
        return project.basePath ?: throw IllegalStateException("Project base path is not set")
    }
}