# Codacy IntelliJ Plugin

The Codacy plugin for IntelliJ helps you review and manage the issues found by Codacy directly within IntelliJ IDEs. It notifies you whether a pull request is up to standards by highlighting problematic code patterns and displaying code quality metrics.

![Codacy IntelliJ Plugin Screenshot](https://github.com/codacy/codacy-intellij-extension/raw/HEAD/.readme/screenshot-01.png)

[Codacy](https://www.codacy.com/) is an automated code review tool that helps your team write high-quality code by analyzing over 40 programming languages, such as PHP, JavaScript, Python, Java, and Ruby. Codacy lets you define and enforce your own quality rules, code patterns, and quality settings to prevent issues in your codebase.

![Codacy Logo](https://github.com/codacy/codacy-intellij-extension/raw/HEAD/.readme/codacy-logo.png)

## Prerequisites

Before installing the plugin, make sure you meet the following requirements:

1.  You have a [Codacy account](https://www.codacy.com/signup-codacy).
2.  The repository you’re working on has been [added to Codacy Cloud](https://docs.codacy.com/organizations/managing-repositories/#adding-a-repository).
3.  You have at least [Repository Read permissions](https://docs.codacy.com/organizations/roles-and-permissions-for-organizations/) for the repository you’re working on.

## Installation

### From JetBrains Marketplace

1.  Open your IntelliJ IDE and navigate to `Settings` > `Plugins`.
2.  Search for "Codacy" in the Marketplace tab.
3.  Click `Install` and restart your IntelliJ IDE.

### Manually Installing

1.  Download the latest `.zip` file from the [releases page](https://github.com/codacy/codacy-intellij-extension/releases).
2.  Open your IntelliJ IDE and navigate to `Settings` > `Plugins`.
3.  Click the gear icon and choose `Install Plugin from Disk...`.
4.  Select the downloaded `.zip` file and restart your IntelliJ IDE.

### Building from Source

1.  Clone the repository from GitHub.
2.  Open the project in your IntelliJ IDE.
3.  Run ./gradlew buildPlugin
4.  The built plugin will be located in the `build/distributions` directory.
5.  Install the plugin manually

## Usage

Ensure Codacy's inspections are enabled by following [this guide](https://www.jetbrains.com/help/idea/code-inspection.html#access-inspections-and-settings).

## Contributing

We welcome all contributions, from small bug fixes to large features.

For information on how to contribute to this project, please refer to the [contributing guidelines](https://github.com/codacy/codacy-intellij-extension/blob/main/CONTRIBUTING.md).

## Troubleshooting

If you're having trouble using the Codacy plugin, see below to troubleshoot errors.

### <span class="skip-vale">Could not</span> find repository

If you see this error, confirm that the repository has been [added to Codacy Cloud](https://docs.codacy.com/organizations/managing-repositories/#adding-a-repository) and that you have at least [Repository Read permissions](https://docs.codacy.com/organizations/roles-and-permissions-for-organizations/).

<!-- Plugin description -->

The Codacy plugin for IntelliJ helps you review and manage the issues found by Codacy directly within your IntelliJ IDE. It notifies you whether a pull request is up to standards by highlighting problematic code patterns and displaying code quality metrics.

<!-- Plugin description end -->
