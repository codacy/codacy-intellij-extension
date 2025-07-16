package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.api.models.IssueThreshold
import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.cli.MacOsCli
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.IconUtils
import com.codacy.intellij.plugin.services.common.TimeoutManager
import com.codacy.intellij.plugin.services.git.PullRequest
import com.codacy.intellij.plugin.services.git.RepositoryManager
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.InetSocketAddress
import java.net.URLEncoder
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel


data class NodeContent(
    val text: String,
    val icon: Icon?= null,
    val filePath: String? = null,
    val tooltip: String? = null
) {
    override fun toString(): String {
        return text
    }
}
class CodacyPullRequestSummaryToolWindowFactory: ToolWindowFactory {

    private var listener: Disposable? = null

    private fun startLocalHttpServer(project: Project, toolWindow: ToolWindow) {
        val configService = service<Config>()
        var port = 8100
        var server: HttpServer? = null

        while (port < 65535) {
            try {
                server = HttpServer.create(InetSocketAddress(port), 0)
                break
            } catch (e: Exception) {
                port++
            }
        }

        if (server == null) throw RuntimeException("Unable to find a free port")

        val timeoutManager = TimeoutManager()
        server.createContext("/token") { exchange ->
            try {
                val token = exchange.requestURI.query.split("=").last()
                if (token.isNotEmpty()) {
                    val repositoryManager = project.service<RepositoryManager>()
                    configService.storeApiToken(token)
                    val response = "Token received and stored. You can now go back to IntelliJ IDEA and use the Codacy plugin"
                    exchange.sendResponseHeaders(200, response.length.toLong())
                    exchange.responseBody.use { os -> os.write(response.toByteArray()) }
                    exchange.close()
                    server.stop(0)
                    timeoutManager.clearTimeout()
                    SwingUtilities.invokeLater {
                        repositoryManager.notifyDidChangeConfig()
                        updateToolWindowContent(project, toolWindow)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log the exception
            }
        }
        server.start()
        val encodedRedirectUrl = URLEncoder.encode("http://localhost:$port/token?token=", "UTF-8")
        val url = "${configService.loginUri}/?redirectUrl=$encodedRedirectUrl"

        BrowserUtil.open(url)
        timeoutManager.startTimeout(60000 * 10) {
            server.stop(0)
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        updateToolWindowContent(project, toolWindow)
    }

    private fun updateToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.removeAllContents(true)
        toolWindow.setIcon(IconUtils.CodacyIcon)
        ApplicationManager.getApplication().invokeLater {
            toolWindow.setIcon(IconUtils.CodacyIcon)
        }
        val panel = JPanel(BorderLayout())


        val buttonsPanel = JPanel(FlowLayout(FlowLayout.LEFT))

        val signInButton = JButton("Sign in")
        signInButton.addActionListener {
            startLocalHttpServer(project, toolWindow)
        }
        buttonsPanel.add(signInButton, BorderLayout.NORTH)

        //TODO this will be only for debug purposes to see what's going on
//        val macOsCli = MacOsCli("/Users/og_pixel/workspace/codacy-intellij-plugin", "gh", "og-pixel", "sandbox", project)
//        val debugInitializeBtn = JButton("Debug initialize")
//        val debugInstallBtn = JButton("Debug install")
//        val debugFindCliCommand = JButton("Debug find cli command")
//        val debugCurlCommand = JButton("Debug curl test")
        val createCLIButton = JButton("Create CLI")


        val initConfigButton = JButton("Reload token")
        initConfigButton.addActionListener { e: ActionEvent? ->
            val configService: Config = service()
            configService.init()
            SwingUtilities.invokeLater {
                val repositoryManager = project.service<RepositoryManager>()
                repositoryManager.notifyDidChangeConfig()
                updateToolWindowContent(project, toolWindow)
            }
        }
        buttonsPanel.add(initConfigButton, BorderLayout.NORTH)
        //TODO delete later
        createCLIButton.addActionListener { e: ActionEvent? ->
            runBlocking {
                val cli = CodacyCli.getInstance("gh", "og-pixel", "sandbox", project)
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("CodacyNotifications")
                    .createNotification(cli.toString(), NotificationType.INFORMATION)
                    .notify(project)
            }
        }
//
//        debugInitializeBtn.addActionListener {e : ActionEvent? ->
//            macOsCli.initialize()
//        }
//        debugInstallBtn.addActionListener { e: ActionEvent? ->
//            runBlocking {
//                macOsCli.install()
//            }
//        }
//        debugFindCliCommand.addActionListener { e: ActionEvent? ->
//            runBlocking {
//                macOsCli.findCliCommand(project)
//            }
//        }
//        debugCurlCommand.addActionListener { e: ActionEvent? ->
//            runBlocking {
//                macOsCli.curlDownload(Config.CODACY_CLI_LINK, "/Users/og_pixel/workspace/download-script.sh", project)
//            }
//        }
//        buttonsPanel.add(debugInstallBtn, BorderLayout.NORTH)
//        //TODO delete later
//        buttonsPanel.add(debugInstallBtn, BorderLayout.NORTH)
//        buttonsPanel.add(debugCurlCommand, BorderLayout.NORTH)
//        buttonsPanel.add(debugInitializeBtn, BorderLayout.NORTH)
//        buttonsPanel.add(debugFindCliCommand, BorderLayout.NORTH)
        buttonsPanel.add(createCLIButton, BorderLayout.NORTH)

        panel.add(buttonsPanel, BorderLayout.NORTH)

        val nodeContent = NodeContent(
            text = "Codacy",
        )
        val rootNode = DefaultMutableTreeNode(nodeContent)
        val treeModel = DefaultTreeModel(rootNode)
        val tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.cellRenderer = CustomIconTreeCellRenderer()

        val configService = service<Config>()
        configService.init()

        if (!configService.storedApiToken.isNullOrBlank()) {
            val repositoryManager = project.service<RepositoryManager>()

            val refreshButton = JButton(AllIcons.General.InlineRefresh)
            refreshButton.addActionListener {
                rootNode.removeAllChildren()
                repositoryManager.pullRequest?.refresh()
            }
            panel.add(refreshButton, BorderLayout.NORTH)

            listener?.dispose()
            listener = repositoryManager.onDidUpdatePullRequest {
                ApplicationManager.getApplication().invokeLater {
                    updateTree(project, rootNode)
                    treeModel.reload()
                }
            }

            updateTree(project, rootNode)
            treeModel.reload()

            panel.add(JBScrollPane(tree), BorderLayout.CENTER)

            tree.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
                        node?.let { openFile(it) }
                    }
                }
            })

            val logOutButton = JButton("Log out")
            logOutButton.addActionListener {
                val confirmed = JOptionPane.showConfirmDialog(
                    logOutButton,
                    "Are you sure you want to log out?",
                    "Confirm Log Out",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                )

                if (confirmed == JOptionPane.YES_OPTION) {
                    rootNode.removeAllChildren()
                    repositoryManager.clear()
                    configService.storeApiToken("")
                    updateToolWindowContent(project, toolWindow)
                }
            }
            panel.add(logOutButton, BorderLayout.SOUTH)
            panel.add(createCLIButton)
        }
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
        panel.revalidate()
        panel.repaint()
        toolWindow.show(null)
    }

    private fun updateTree(project: Project, rootNode: DefaultMutableTreeNode) {
        val repositoryManager = project.service<RepositoryManager>()
        rootNode.removeAllChildren()
        repositoryManager.pullRequest?.let { pr ->
            if (pr.prWithAnalysis != null) {
                val nodeContent = NodeContent(
                    text = "Pull request: #${pr.meta?.pullRequest!!.number}",
                )
                rootNode.add(DefaultMutableTreeNode(nodeContent))
                getInformationNodes(rootNode, pr)
                getIssuesNodes(rootNode, pr)
                getCoverageNodes(rootNode, pr)
                getDuplicationNodes(rootNode, pr)
                getComplexityNodes(rootNode, pr)
            } else {
                val nodeContent = NodeContent(
                    text = "No open pull request found.",
                )
                rootNode.add(DefaultMutableTreeNode(nodeContent))
            }
        }
    }

    private fun getInformationNodes(rootNode: DefaultMutableTreeNode, pr: PullRequest) {
        var loadingIcon = AnimatedIcon(
            250,
            AllIcons.Actions.Refresh,
            RotatedIcon(AllIcons.Actions.Refresh, -90.0)
        )
        var nodeContent = NodeContent(
            text = when {
                pr.prWithAnalysis?.isAnalysing == true -> "Analyzing..."
                pr.prWithAnalysis?.isUpToStandards == true -> "Up to standards."
                pr.prWithAnalysis?.isUpToStandards == false -> "Not up to standards."
                else -> "Not analysed."
            },
            icon = when {
                pr.prWithAnalysis?.isAnalysing == true -> loadingIcon
                pr.prWithAnalysis?.isUpToStandards == true -> AllIcons.General.InspectionsOK
                pr.prWithAnalysis?.isUpToStandards == false -> AllIcons.General.BalloonError
                else -> AllIcons.General.BalloonInformation
            },
            filePath=null
        )
        val parentNode = DefaultMutableTreeNode(nodeContent)
        rootNode.add(parentNode)

        if (pr.gates?.qualityGate?.issueThreshold != null && (pr.prWithAnalysis?.newIssues ?: 0) > 0) {
            val gate: IssueThreshold = pr.gates!!.qualityGate.issueThreshold
            nodeContent = NodeContent(
                text = (gate.minimumSeverity ?: "Info") + " Issues > ${gate.threshold}",
                icon = AllIcons.Actions.StartDebugger,
                filePath = null
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }

        if (pr.gates?.qualityGate?.securityIssueThreshold?.let { it > 0 } == true && (pr.prWithAnalysis?.newIssues ?: 0) > 0) {
            val gate: Int = pr.gates!!.qualityGate.securityIssueThreshold
            nodeContent = NodeContent(
                text = "Security issues > $gate",
                icon = AllIcons.Diff.Lock,
                filePath = null
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }

        if (pr.gates?.qualityGate?.complexityThreshold?.let { it > 0 } == true && (pr.prWithAnalysis?.newIssues ?: 0) > 0) {
            val gate: Int = pr.gates!!.qualityGate.complexityThreshold
            nodeContent = NodeContent(
                text="Complexity > $gate",
                icon=AllIcons.Toolwindows.ToolWindowHierarchy,
                filePath=null
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }

        if (pr.gates?.qualityGate?.duplicationThreshold?.let { it > 0 } == true && (pr.prWithAnalysis?.newIssues ?: 0) > 0) {
            val gate: Int = pr.gates!!.qualityGate.duplicationThreshold
            nodeContent = NodeContent(
                text="Duplication > $gate",
                icon=AllIcons.Actions.Copy,
                filePath=null
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }

        pr.prWithAnalysis?.coverage?.resultReasons
            ?.filter { r -> !r.isUpToStandards }
            ?.forEach { r ->
                nodeContent = NodeContent(
                    text = "Coverage ${r.gate} < ${r.expected}",
                    icon = AllIcons.RunConfigurations.TrackCoverage,
                    filePath = null
                )
                val node = DefaultMutableTreeNode(nodeContent)
                parentNode.add(node)
            }

        rootNode.add(parentNode)
    }

    private fun getIssuesNodes(rootNode: DefaultMutableTreeNode, pr: PullRequest) {
        var nodeContent = NodeContent(
            text = "${pr.prWithAnalysis?.newIssues ?: 0} new issues (${pr.prWithAnalysis?.fixedIssues ?: 0} fixed)",
            icon = AllIcons.Actions.StartDebugger,
        )
        val parentNode = DefaultMutableTreeNode(nodeContent)
        rootNode.add(parentNode)

        val files = pr.files
            .filter { it.quality.deltaNewIssues.let { delta -> delta > 0 } }
            .sortedByDescending { it.quality.deltaNewIssues }

        files.forEach { file ->
            val deltaNewIssues = file.quality.deltaNewIssues
            val deltaIssuesText = if (deltaNewIssues > 0) "+$deltaNewIssues" else deltaNewIssues.toString()
            val (fileName) = getFileInfo(file.file.path)
            nodeContent = NodeContent(
                text = "$fileName $deltaIssuesText",
                filePath = file.file.path,
                tooltip = file.file.path
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }
        rootNode.add(parentNode)
    }

    private fun getDuplicationNodes(rootNode: DefaultMutableTreeNode, pr: PullRequest) {
        val text = if (pr.prWithAnalysis!!.deltaClonesCount > 0) {
            "${pr.prWithAnalysis!!.deltaClonesCount} new clones"
        } else if (pr.prWithAnalysis!!.deltaClonesCount < 0) {
            "${-pr.prWithAnalysis!!.deltaClonesCount} fixed clones"
        } else {
            "No new clones"
        }
        var nodeContent = NodeContent(
            text = text,
            icon = AllIcons.Actions.Copy,
        )
        val parentNode = DefaultMutableTreeNode(nodeContent)
        rootNode.add(parentNode)

        val files = pr.files
            .filter { it.quality.deltaClonesCount.let { delta -> delta > 0 } }
            .sortedByDescending { it.quality.deltaClonesCount }

        files.forEach { file ->
            val deltaClonesCount = file.quality.deltaClonesCount
            val deltaClonesText = if (deltaClonesCount > 0) "+$deltaClonesCount" else deltaClonesCount.toString()
            val (fileName) = getFileInfo(file.file.path)
            nodeContent = NodeContent(
                text = "$fileName $deltaClonesText",
                filePath = file.file.path,
                tooltip = file.file.path
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }
        rootNode.add(parentNode)
    }

    private fun getComplexityNodes(rootNode: DefaultMutableTreeNode, pr: PullRequest) {
        val text = if (pr.prWithAnalysis!!.deltaComplexity > 0) {
            "${pr.prWithAnalysis!!.deltaComplexity} complexity increase"
        } else {
            "No complexity information"
        }
        var nodeContent = NodeContent(
            text = text,
            icon = AllIcons.Toolwindows.ToolWindowHierarchy,
        )
        val parentNode = DefaultMutableTreeNode(nodeContent)
        rootNode.add(parentNode)

        val files = pr.files
            .filter { it.quality.deltaComplexity.let { delta -> delta > 0 } }
            .sortedByDescending { it.quality.deltaComplexity }

        files.forEach { file ->
            val deltaComplexity = file.quality.deltaComplexity
            val deltaComplexityText = if (deltaComplexity > 0) "+$deltaComplexity" else deltaComplexity.toString()
            val (fileName) = getFileInfo(file.file.path)
            nodeContent = NodeContent(
                text = "$fileName $deltaComplexityText",
                filePath = file.file.path,
                tooltip = file.file.path
            )
            val node = DefaultMutableTreeNode(nodeContent)
            parentNode.add(node)
        }
        rootNode.add(parentNode)
    }

    private fun getCoverageNodes(rootNode: DefaultMutableTreeNode, pr: PullRequest) {
        val messages: MutableList<String> = mutableListOf()
        if (pr.prWithAnalysis!!.coverage?.diffCoverage?.cause == "ValueIsPresent") {
            messages.add("${pr.prWithAnalysis!!.coverage?.diffCoverage?.value}% diff coverage")
        }
        if (pr.prWithAnalysis!!.coverage?.deltaCoverage?.let { it != 0.toDouble() } == true) {
            messages.add("${if (pr.prWithAnalysis!!.coverage?.deltaCoverage?.let { it > 0 } == true) "+" else ""}${pr.prWithAnalysis!!.coverage?.deltaCoverage}% variation")
        }
        val text = if (messages.isNotEmpty()) {
            messages.joinToString(", ")
        } else {
            "No coverage information"
        }
        var nodeContent = NodeContent(
            text = text,
            icon = AllIcons.RunConfigurations.TrackCoverage,
        )
        val parentNode = DefaultMutableTreeNode(nodeContent)
        rootNode.add(parentNode)

        val files = pr.files
            .filter { file ->
                file.coverage.deltaCoverage.let { it.toInt() != 0 } || file.coverage.totalCoverage.let { it.toInt() != 0 }
            }
            .sortedWith { a, b ->
                when {
                    a.coverage.deltaCoverage.let { it.toInt() != 0 } && b.coverage.deltaCoverage.let { it.toInt() != 0 } ->
                        a.coverage.deltaCoverage.compareTo(b.coverage.deltaCoverage)

                    a.coverage.totalCoverage.let { it.toInt() != 0 } && b.coverage.totalCoverage.let { it.toInt() != 0 } ->
                        ((b.coverage.totalCoverage - a.coverage.totalCoverage + 1000).toInt())

                    else -> 0
                }
            }

        files.forEach { file ->
            val description = when {
                file.coverage.deltaCoverage.let { it.toInt() != 0 } -> {
                    val deltaCoverage = file.coverage.deltaCoverage
                    val sign = if (deltaCoverage > 0) "+" else ""
                    "$sign$deltaCoverage% variation"
                }

                file.coverage.totalCoverage.let { it.toInt() != 0 } -> {
                    "${file.coverage.totalCoverage}% total"
                }

                else -> null
            }

            if (description != null) {
                val (fileName) = getFileInfo(file.file.path)
                nodeContent = NodeContent(
                    text = "$fileName $description",
                    filePath = file.file.path,
                    tooltip = file.file.path
                )
                val node = DefaultMutableTreeNode(nodeContent)
                parentNode.add(node)
            }
        }
    }

    private fun getFileInfo(filePath: String): Pair<String, String> {
        val file = File(filePath)
        val fileName = file.name
        val location = file.parent ?: ""

        return Pair(fileName, location)
    }

    private fun openFile(node: DefaultMutableTreeNode) {
        val nodeContent = node.userObject as? NodeContent ?: return
        if (nodeContent.filePath == null) return

        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val projectBasePath = project.basePath ?: return

        val fullPath = "$projectBasePath/${nodeContent.filePath}"
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(fullPath) ?: return

        ApplicationManager.getApplication().invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openTextEditor(OpenFileDescriptor(project, virtualFile), true)
        }
    }

}
class CustomIconTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        if (value is DefaultMutableTreeNode && value.userObject is NodeContent) {
            val nodeContent = value.userObject as NodeContent
            icon = nodeContent.icon
            toolTipText = nodeContent.tooltip
        }
        if (!selected) backgroundNonSelectionColor = UIManager.getColor("Panel.background")
        return this
    }
}
