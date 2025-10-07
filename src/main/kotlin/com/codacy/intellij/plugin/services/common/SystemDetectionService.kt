package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.application.ApplicationNamesInfo

enum class OsType {
    Windows,
    MacOS,
    Linux,
    Unknown;

    override fun toString(): String {
        return when (this) {
            Windows -> "Windows"
            MacOS -> "MacOS"
            Linux -> "Linux"
            Unknown -> "Unknown"
        }
    }


    companion object {
        fun fromString(osName: String): OsType {
            return when (osName.lowercase()) {
                "windows" -> Windows
                "macos", "mac os", "mac" -> MacOS
                "linux" -> Linux
                else -> Unknown // Default to Unknown if OS is not recognized
            }
        }
    }
}


enum class IdeType {
    Intellij,
    Webstorm,
    Pycharm,
    Phpstorm,
    Rubymine,
    Goland,
    Rider,
    Clion,
    Datagrip,
    Dataspell,
    Unknown;

    override fun toString(): String {
        return when (this) {
            Intellij -> "IntelliJ"
            Webstorm -> "WebStorm"
            Pycharm -> "PyCharm"
            Phpstorm -> "PhpStorm"
            Rubymine -> "RubyMine"
            Goland -> "GoLand"
            Rider -> "Rider"
            Clion -> "CLion"
            Datagrip -> "DataGrip"
            Dataspell -> "DataSpell"
            Unknown -> "Unknown"
        }
    }

    companion object {

        fun fromString(ideName: String): IdeType {
            return when (ideName.lowercase()) {
                "intellij" -> Intellij
                "webstorm" -> Webstorm
                "pycharm" -> Pycharm
                "phpstorm" -> Phpstorm
                "rubymine" -> Rubymine
                "goland" -> Goland
                "rider" -> Rider
                "clion" -> Clion
                "datagrip" -> Datagrip
                "dataspell" -> Dataspell
                else -> Unknown
            }
        }
    }
}


object SystemDetectionService {


    fun detectOs(): OsType {
        val systemOs = System.getProperty("os.name").lowercase()
        return when {
            systemOs.contains("win") || systemOs.contains("windows") -> OsType.Windows
            systemOs.contains("mac os x") || systemOs.contains("darwin") -> OsType.MacOS
            listOf("nux", "nix", "aix", "linux").any { systemOs.contains(it) } -> OsType.Linux
            else -> OsType.Unknown
        }
    }

    fun detectIde(): IdeType = IdeType
        .fromString(
            ApplicationNamesInfo.getInstance().productName.lowercase()
        )


}
