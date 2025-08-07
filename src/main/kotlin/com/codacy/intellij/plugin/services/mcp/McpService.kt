package com.codacy.intellij.plugin.services.mcp

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.mcp.model.*
import com.google.gson.Gson
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Service
class McpService {

    companion object {
        val GITHUB_COPILOT_PLUGIN_ID = PluginId.getId("com.github.copilot") //TODO check
        val JUNIE_PLUGIN_ID = PluginId.getId("com.codacy.intellij.plugin") //TODO path
    }


    //TODO this will be agnostic and use any agent
    fun createConfiguration(aiAgent: AiAgent) {
        val fullPath = aiAgent.configurationPath
        val mcpConfigFullPath = aiAgent.configurationFilePath


        if (!fullPath.exists()) {
            File(fullPath.toString()).mkdirs()
        }


        val mcpJson = McpServer(
            command = "npx",
            args = listOf("-y", "@codacy/codacy-mcp"),
            env = mapOf(
                "CODACY_ACCOUNT_TOKEN" to "PUT TOKEN HERE", //TODO read from config
            )
        )

        val gson = Gson()

        //TODO some notification and maybe return value to indicate if it went well
        if (mcpConfigFullPath.exists()) {
            val mcpConfigFile = File(mcpConfigFullPath.toString())
            val mcpConfigJson = mcpConfigFile.readText()

            val mcpConfig = gson.fromJson(mcpConfigJson, McpConfig::class.java)
            val updated = mcpConfig.copy(mcpServers = mcpConfig.mcpServers.plus("codacy" to mcpJson))

            File(mcpConfigFullPath.toString()).writeText(gson.toJson(updated))
        } else {
            val mcpConfig = McpConfig(
                mcpServers = mapOf("codacy" to mcpJson)
            )
            val mcpConfigJson = gson.toJson(mcpConfig)
            File(mcpConfigFullPath.toString()).writeText(mcpConfigJson)
        }
    }


    //TODO these params might be just part of the class later
    fun createOrUpdateRules(project: Project, aiAgent: AiAgent, params: RepositoryParams?) {

        //TODO this part is from vscode and it checks if user wants to generate rules
//        const analyzeGeneratedCode = vscode.workspace.getConfiguration().get('codacy.guardrails.analyzeGeneratedCode')
//        const generateRules = vscode.workspace.getConfiguration().get('codacy.guardrails.rulesFile')
//
//        if (generateRules === 'disabled') return

        val newRules = newRulesTemplate(
            project = project, //TODO get the current project
            repositoryParams = params,
            excludedScopes = listOf(RuleScope.GUARDRAILS) //TODO this should be configurable
        )

        val rulesPath = aiAgent.getAiAgentSuggestionFilePath(project)
        val dirPath = rulesPath.parent

        if (!dirPath.exists()) {
            dirPath.toFile().mkdirs()
        }


        if (!rulesPath.exists()) {
            rulesPath.writeText(
                convertRulesToMarkdown(newRules)
            )
            //TODO force cast
            addRulesToGitignore(project.basePath!!, rulesPath)
        } else {
            try {
                val existingContent = rulesPath.readText()
                rulesPath.writeText(convertRulesToMarkdown(newRules, existingContent))
            } catch (e: Exception) {
                //TODO in vscode there might be parsing error
            }
        }

    }

    //TODO try to remove project from the parameters
    fun newRulesTemplate(
        project: Project,
        repositoryParams: RepositoryParams?,
        excludedScopes: List<RuleScope>?
    ): RuleConfig {
        val repositoryRules = mutableListOf<Rule>()

        if (repositoryParams != null) {
            repositoryRules.add(
                Rule(
                    `when` = "using any tool that accepts the arguments: `provider`, `organization`, or `repository`",
                    enforce = listOf(
                        "ALWAYS use:",
                        "- provider: ${repositoryParams.provider}",
                        "- organization: ${repositoryParams.organization}",
                        "- repository: ${repositoryParams.repository}",
                        "Avoid calling `git remote -v` unless really necessary",
                    ),
                    scope = RuleScope.GENERAL
                )
            )
        }


        //TODO force cast
        val codacyCLISettingsPath = Paths.get(
            project.basePath!!,
            Config.CODACY_DIRECTORY_NAME,
            Config.CODACY_YAML_NAME
        )

        val enigmaRules = mutableListOf<Rule>()

        //TODO this function exists in CLI service, maybe we can use it
        if (codacyCLISettingsPath.exists()) {
            val codacyCLISettingsFile = File(codacyCLISettingsPath.toString()).readText()
            if (codacyCLISettingsFile.contains("enigma")) {
                enigmaRules.add(
                    Rule(
                        `when` = "When user asks to create a rule",
                        scope = RuleScope.GENERAL,
                        enforce = listOf(
                            "To add a new rule for code analysis, follow these steps:",
                            "- Create or edit a file named `enigma.yaml` in the root of the project.",
                            "- Each rule should be listed under the `rules:` key as an item in a YAML list.",
                            //TODO make sure this formatting is correct
                            //TODO this is slightly incorrect, left space between dollar and keyword
                            """- Example rule format:
                        ```yaml
                        rules:,
                          - Id: python_hardcoded_password,
                            Pattern: $ PASSWORD = $ VALUE, 
                            Description: Detects hardcoded passwords in string variable declarations,
                            Category: Security,
                            MetaTags:,
                              - Id: PASSWORD,
                                Regex: ...,
                              - Id: VALUE,
                                Regex: ...,
                            Languages:,
                              - python,
                        ```""",
                            "Pattern Field",
                            "- The `Pattern` is NOT a regex. It is a literal code pattern, but you can use MetaTags (like `\$PASSWORD` or `\$VALUE`) as placeholders.",
                            "- MetaTags must start with a `\$` and be defined in the `MetaTags` section.",
                            "MetaTags",
                            "- Every MetaTag used in the `Pattern` must have a definition under `MetaTags`.",
                            "- Each MetaTag must have an `Id` and a `Regex`.",
                            "- The `Regex` must be Perl-compatible (PCRE), but negative lookaheads are NOT supported. Please always wrap in double quotes.",
                            "Languages:  List the programming languages this rule applies to under `Languages`.",
                            "Testing Your Rule: After creating or editing a rule, test it by running the codacy_cli_analyze tool with:",
                            "- rootPath set to the project root",
                            "- no file",
                            "- tool set to \"codacy-enigma-cli\"",
                            "Check the output for any parsing errors and fix them if needed.",
                            "Summary - All rules must:",
                            "- Be in `enigma.yaml` at the project root",
                            "- Define all MetaTags used in the Pattern",
                            "- Use only supported regex features in MetaTags",
                            "- Be tested for parsing errors using the CLI",
                        )
                    )
                )
            }
        }


        val commonRules: List<Rule> = listOf(
            Rule(
                `when` = "CRITICAL: After ANY successful `edit_file` or `reapply` operation",
                enforce = listOf(
                    "YOU MUST IMMEDIATELY run the `codacy_cli_analyze` tool from Codacy's MCP Server for each file that was edited, with:",
                    "- `rootPath`: set to the workspace path",
                    "- `file`: set to the path of the edited file",
                    "- `tool`: leave empty or unset",
                    "If any issues are found in the new edits, propose and apply fixes for them.",
                    "> NOTE: Failure to follow this rule is considered a critical error.",
                ),
                scope = RuleScope.GUARDRAILS,
            ),
            Rule(
                `when` = "After every response",
                enforce = listOf(
                    "If you made any file edits in this conversation, verify you ran `codacy_cli_analyze` tool from Codacy's MCP Server ",
                ),
                scope = RuleScope.GUARDRAILS,
            ),
            Rule(
                `when` = "When there are no Codacy MCP Server tools available, or the MCP Server is not reachable",
                enforce = listOf(
                    "Suggest the user the following troubleshooting steps:",
                    "- Try to reset the MCP on the extension",
                    "- If the user is using VSCode, suggest them to review their Copilot > MCP settings in Github, under their organization or personal account. Refer them to Settings > Copilot > Enable MCP servers in Copilot. Suggested URL (https://github.com/settings/copilot/features) or https://github.com/organizations/{organization-name}/settings/copilot/features (This can only be done by their organization admins / owners)",
                    "If none of the above steps work, suggest the user to contact Codacy support",
                ),
                scope = RuleScope.GUARDRAILS,
            ),
            Rule(
                `when` = "Trying to call a tool that needs a rootPath as a parameter",
                enforce = listOf("Always use the standard, non-URL-encoded file system path"),
                scope = RuleScope.GUARDRAILS,
            ),
            Rule(
                `when` = "CRITICAL: Dependencies and Security Checks",
                enforce = listOf(
                    "IMMEDIATELY after ANY of these actions:",
                    "- Running npm/yarn/pnpm install",
                    "- Adding dependencies to package.json",
                    "- Adding requirements to requirements.txt",
                    "- Adding dependencies to pom.xml",
                    "- Adding dependencies to build.gradle",
                    "- Any other package manager operations",
                    "You MUST run the `codacy_cli_analyze` tool with:",
                    "- `rootPath`: set to the workspace path",
                    "- `tool`: set to \"trivy\"",
                    "- `file`: leave empty or unset",
                    "If any vulnerabilities are found because of the newly added packages:",
                    "- Stop all other operations",
                    "- Propose and apply fixes for the security issues",
                    "- Only continue with the original task after security issues are resolved",
                    "EXAMPLE:",
                    "- After: npm install react-markdown",
                    "- Do: Run codacy_cli_analyze with trivy",
                    "- Before: Continuing with any other tasks",
                ),
                scope = RuleScope.GUARDRAILS,
            ),
            Rule(
                enforce = listOf(
                    "Repeat the relevant steps for each modified file.",
                    "\"Propose fixes\" means to both suggest and, if possible, automatically apply the fixes.",
                    "You MUST NOT wait for the user to ask for analysis or remind you to run the tool.",
                    "Do not run `codacy_cli_analyze` looking for changes in duplicated code or code complexity metrics.",
                    "Do not run `codacy_cli_analyze` looking for changes in code coverage.",
                    "Do not try to manually install Codacy CLI using either brew, npm, npx, or any other package manager.",
                    "If the Codacy CLI is not installed, just run the `codacy_cli_analyze` tool from Codacy's MCP Server.",
                    "When calling `codacy_cli_analyze`, only send provider, organization and repository if the project is a git repository.",
                ),
                scope = RuleScope.GUARDRAILS,
            ),
            Rule(
                `when` = "Whenever a call to a Codacy tool that uses `repository` or `organization` as a parameter returns a 404 error",
                enforce = listOf(
                    "Offer to run the `codacy_setup_repository` tool to add the repository to Codacy",
                    "If the user accepts, run the `codacy_setup_repository` tool",
                    "Do not ever try to run the `codacy_setup_repository` tool on your own",
                    "After setup, immediately retry the action that failed (only retry once)",
                ),
                scope = RuleScope.GENERAL,
            ),
        )

        val allRulesFiltered = (repositoryRules + commonRules + enigmaRules)/*.filter {
            //TODO make sure this makes sense
            !(excludedScopes?.contains(it.scope) ?: false)
        }*/

        return RuleConfig(
            name = "Codacy Rules",
            description = "Configuration for AI behavior when interacting with Codacy's MCP Server",
            rules = allRulesFiltered,
        )

    }

    private fun convertRulesToMarkdown(rules: RuleConfig, existingContent: String? = null): String {
        val newCodacyRules = buildString {
            append("\n# ${rules.name}\n${rules.description}\n\n")
            append(
                rules.rules.joinToString("\n\n") { rule ->
                    val whenSection = rule.`when`?.let { "## $it\n" } ?: "## General\n"
                    val enforceSection = rule.enforce.joinToString("\n") { e ->
                        if (e.startsWith("-")) " $e" else "- $e"
                    }
                    "$whenSection$enforceSection"
                }
            )
            append("\n")
        }

        if (existingContent.isNullOrEmpty()) {
            return "---$newCodacyRules---"
        }

        val existingRules = existingContent.split("---")
        return existingRules.joinToString("---") { content ->
            if (content.contains(rules.name)) newCodacyRules else content
        }
    }

    private fun addRulesToGitignore(projectPath: String, rulesPath: Path) {
        val currentIDE = "IntelliJ"
        //val workspacePath = //rulesPath.parent.toString()
        val gitignorePath = Paths.get(projectPath, ".gitignore")
        val relativeRulesPath = rulesPath.toAbsolutePath().toString().removePrefix(projectPath+ File.separator)
        val gitignoreContent = "\n\n# Ignore $currentIDE AI rules\n$relativeRulesPath\n"
        var existingGitignore: String
        if (gitignorePath.exists()) {
            existingGitignore = gitignorePath.readText()

            if (!existingGitignore.split("\n").any { it.trim() == relativeRulesPath.trim() }) {
                gitignorePath.toFile().appendText(gitignoreContent)
            }
        } else {
            gitignorePath.writeText(gitignoreContent)
        }
    }

    private fun checkForNode(): Boolean =
        ProcessBuilder("node", "--version")
            .start()
            .waitFor() == 0


    private fun getPresentAiAgents(): List<AiAgent> {
        return listOf(
            JUNIE_PLUGIN_ID to AiAgent.JUNIE,
            GITHUB_COPILOT_PLUGIN_ID to AiAgent.GITHUB_COPILOT
        ).mapNotNull { (id, agent) ->
            if (PluginManagerCore.getPlugin(id) != null) agent else null
        }
    }


}
