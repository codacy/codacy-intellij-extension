package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.telemetry.ExtensionInstalledEvent
import com.codacy.intellij.plugin.telemetry.ExtensionUninstalledEvent
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class PluginLifecycleListener : StartupActivity {
    override fun runActivity(project: Project) {
        PluginInstaller.addStateListener(object : PluginStateListener {
            override fun install(ideaPluginDescriptor: IdeaPluginDescriptor) {
                if (ideaPluginDescriptor.pluginId.idString == "com.codacy.intellij.plugin") {
                    val os = System.getProperty("os.name") ?: "unknown"
                    Telemetry.track(ExtensionInstalledEvent(os = os))
                }
            }

            override fun uninstall(ideaPluginDescriptor: IdeaPluginDescriptor) {
                if (ideaPluginDescriptor.pluginId.idString == "com.codacy.intellij.plugin") {
                    Telemetry.track(ExtensionUninstalledEvent)
                }
            }
        })
    }
}
