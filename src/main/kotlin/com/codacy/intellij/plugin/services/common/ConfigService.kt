package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.State

@State(
    name = "MyPluginConfig",
    storages = [Storage("MyPluginConfig.xml")]
)
@Service(Service.Level.APP)
class ConfigService : PersistentStateComponent<Config> {

    private var config = Config()

    override fun getState(): Config = config

    override fun loadState(state: Config) {
        this.config = state
    }

    companion object {
        fun getInstance(): ConfigService = ServiceManager.getService(ConfigService::class.java)
    }
}
