package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.telemetry.*
import com.intellij.ide.plugins.*

@DynamicallyLoaded
class PluginStateListener : DynamicPluginListener {

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        StartupListener.cancelAllScopes()
        Telemetry.track(ExtensionUnloadedEvent)

        super.beforePluginUnload(pluginDescriptor, isUpdate)
    }
}