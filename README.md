# Codacy IntelliJ IDEA Extension

The Codacy extension for IntelliJ IDEA helps you review and manage the issues found by Codacy directly within Visual Studio Code. It notifies you whether a pull request is up to standards by highlighting problematic code patterns and displaying code quality metrics.

![Codacy IntelliJ IDEA Extension Screenshot](https://github.com/codacy/codacy-intellij-extension/raw/HEAD/.readme/screenshot-01.png)

[Codacy](https://www.codacy.com/) is an automated code review tool that helps your team write high-quality code by analyzing over 40 programming languages, such as PHP, JavaScript, Python, Java, and Ruby. Codacy lets you define and enforce your own quality rules, code patterns, and quality settings to prevent issues in your codebase.

![Codacy Logo](https://github.com/codacy/codacy-intellij-extension/raw/HEAD/.readme/codacy-logo.png)

## Prerequisites

Before installing the extension, make sure you meet the following requirements:

1. You have a [Codacy account](https://www.codacy.com/signup-codacy).
2. The repository you’re working on has been [added to Codacy Cloud](https://docs.codacy.com/organizations/managing-repositories/#adding-a-repository).
3. You have at least [Repository Read permissions](https://docs.codacy.com/organizations/roles-and-permissions-for-organizations/) for the repository you’re working on.
4. IntelliJ IDEA, version 2023.3.3 or later.

## Installation

### From JetBrains Marketplace

1. Open IntelliJ IDEA and navigate to `Settings` > `Plugins`.
2. Search for "Codacy" in the Marketplace tab.
3. Click `Install` and restart IntelliJ IDEA.

### Manually Installing

1. Download the latest `.zip` file from the [releases page](https://github.com/codacy/codacy-intellij-extension/releases).
2. Open IntelliJ IDEA and navigate to `Settings` > `Plugins`.
3. Click the gear icon and choose `Install Plugin from Disk...`.
4. Select the downloaded `.zip` file and restart IntelliJ IDEA.

### Building from Source

1. Clone the repository from GitHub.
2. Open the project in IntelliJ IDEA.
3. Run ./gradlew buildPlugin
4. The built plugin will be located in the `build/distributions` directory.
5. Install the plugin manually

## Usage

For detailed information on how to use this extension, please refer to the [official documentation](https://docs.codacy.com/getting-started/integrating-codacy-with-intellij-idea/).
Ensure Codacy's inspections are enabled by following [this guide](https://www.jetbrains.com/help/idea/code-inspection.html#access-inspections-and-settings).

## Contributing

We welcome all contributions, from small bug fixes to large features.

For information on how to contribute to this project, please refer to the [contributing guidelines](https://github.com/codacy/codacy-intellij-extension/blob/main/CONTRIBUTING.md).

## Troubleshooting

If you're having trouble using the Codacy extension for VS Code, see below to troubleshoot errors.

### <span class="skip-vale">Could not</span> find repository

If you see this error, confirm that the repository has been [added to Codacy Cloud](https://docs.codacy.com/organizations/managing-repositories/#adding-a-repository) and that you have at least [Repository Read permissions](https://docs.codacy.com/organizations/roles-and-permissions-for-organizations/).

<!-- Plugin description -->

The Codacy extension for IntelliJ IDEA helps you review and manage the issues found by Codacy directly within Visual Studio Code. It notifies you whether a pull request is up to standards by highlighting problematic code patterns and displaying code quality metrics.

<!-- Plugin description end -->
