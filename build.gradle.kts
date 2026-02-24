import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatformGradlePlugin)
    alias(libs.plugins.changelog)
    alias(libs.plugins.kover)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    intellijPlatform {
        defaultRepositories()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation(libs.qodana.sarif)
    implementation("com.segment.analytics.kotlin:core:1.21.0")
//    implementation("com.jetbrains.qodana:qodana-sarif:0.2.8")

    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))
        bundledPlugins(properties("platformBundledPlugins").map {
            it.split(',').map(String::trim).filter(String::isNotEmpty)
        })
        plugins(properties("platformPlugins").map {
            it.split(',').map(String::trim).filter(String::isNotEmpty)
        })
    }
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    @Suppress("UnstableApiUsage") jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

// IntelliJ Platform Gradle Plugin (https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased()).withHeader(false).withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map {
            listOf(it.split('-').getOrElse(1) { "default" }.split('.').first())
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

// Configure UI tests plugin
// Read more: https://github.com/JetBrains/intellij-ui-test-robot
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                systemProperty("robot-server.port", "8082")
                systemProperty("ide.mac.message.dialogs.as.sheets", "false")
                systemProperty("jb.privacy.policy.text", "<!--999.999-->")
                systemProperty("jb.consents.confirmation.enabled", "false")
            }
        }
    }
}