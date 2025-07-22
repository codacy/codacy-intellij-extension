package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.intellij.openapi.components.Service

@Service
class WinCodacyCli : CodacyCli() {
    companion object {
        private const val NOT_SUPPORTED = "Codacy CLI on native Windows is not supported. Use WSL."
    }

    override suspend fun prepareCli(autoInstall: Boolean) {
        throw NotImplementedError(NOT_SUPPORTED)
    }

    override suspend fun installCli(): String? {
        throw NotImplementedError(NOT_SUPPORTED)
    }

    override suspend fun analyze(file: String?, tool: String?): List<ProcessedSarifResult>? {
        throw NotImplementedError(NOT_SUPPORTED)
    }

//    override suspend fun execAsync(command: String, args: Map<String, String>?): Result<Pair<String, String>> {
//        throw NotImplementedError(NOT_SUPPORTED)
//    }
}
