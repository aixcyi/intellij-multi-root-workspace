package net.navifox.plugins.workspace

import com.intellij.icons.AllIcons
import com.intellij.ide.SelectInTarget
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.projectView.NodeSortOrder
import com.intellij.ide.projectView.NodeSortSettings
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPaneWithAsyncSupport
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructureBase
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.tree.LeafState
import com.intellij.ui.treeStructure.Tree
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.MrWorkspace
import net.navifox.plugins.core.MrWorkspaceUnusableException
import net.navifox.plugins.core.loadWorkspace
import net.navifox.plugins.core.resolveFolderPath
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultTreeModel

/**
 * 多根工作区视图。
 *
 * 提供一个像 Visual Studio Code 那样的、可以同时显示多个不同根目录的文件夹的
 * [工作区目录](https://code.visualstudio.com/docs/editing/workspaces/workspaces)，并且操作体验与
 * JetBrains IDE 原生目录树体验一致；呈现为
 * [“项目”工具窗口](https://www.jetbrains.com/help/idea/project-tool-window.html)
 * 下拉选项的其中一个选项。
 *
 * 读取项目根目录下的 `*.code-workspace` 文件中的 `folders` 数组。
 * 只有一个文件时自动使用，有多个且未指定时按文件名顺序取第一个并气泡提醒。
 * 解析失败时自动顺延到下一个候选文件；全部失败时在面板提示配置文件不可用。
 *
 * 注意：这个类不为多工作区设计，只能显示单个工作区。
 */
class MrWorkspaceViewPane(project: Project) : AbstractProjectViewPaneWithAsyncSupport(project) {

    companion object {
        const val ID = "MrWorkspaceViewPane"
        const val NOTIFICATION_GROUP_ID = "MrWorkspaceView"
    }

    override fun getTitle(): String = NavifoxMessageBundle.message("MrWorkspaceViewPane.title")

    override fun getId(): String = ID

    override fun getIcon(): Icon = AllIcons.Nodes.Workspace

    override fun getWeight(): Int = 1

    override fun createComponent() = super.createComponent().also {
        MrWorkspacePanes.register(this)
    }

    override fun dispose() {
        MrWorkspacePanes.unregister(this)
        super.dispose()
    }

    override fun createStructure(): AbstractTreeStructureBase = MrWorkspaceTreeStructure()

    override fun createTree(model: DefaultTreeModel): DnDAwareTree = ProjectViewTree(model)

    // `SelectInTarget` 用于提供“在此视图中选择”（Alt + F1）的功能：与注册到扩展点
    // com.intellij.selectInTarget 的全局目标同为一个类（MrWorkspaceSelectInTarget），行为一致。
    override fun createSelectInTarget(): SelectInTarget = MrWorkspaceSelectInTarget(myProject)

    /**
     * 最近一次成功加载的工作区根目录；为空表示当前没有可展示的文件夹。
     * 供 [MrWorkspaceSelectInTarget] 判断目标文件是否可见，随每次加载/刷新更新。
     */
    @Volatile
    internal var workspaceRootDirectories: List<VirtualFile> = emptyList()

    // 我记得是工具栏菜单
    override fun addToolbarActions(group: DefaultActionGroup) {
        group.add(object : AnAction(
            NavifoxMessageBundle.message("MrWorkspaceViewPane.refresh"),
            NavifoxMessageBundle.message("MrWorkspaceViewPane.refresh.description"),
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                updateFromRoot(true)
            }
        })
    }

    /**
     * 判断其它 [MrWorkspaceViewPane] 跟当前实例属不属于同一个 [Project]。
     */
    internal infix fun belongsTo(project: Project): Boolean = myProject === project

    override fun createComparator(): Comparator<NodeDescriptor<*>> {
        // 顶层条目（正常与缺失的文件夹节点）之间保持 `folders` 数组顺序；其余比较（目录内部
        // 子节点等）交给平台比较器，使其实时响应“项目”工具窗口的“排序依据”菜单与文件夹置顶等设置。
        val platformComparator = GroupByTypeComparator(myProject, ID)
        return Comparator { a, b ->
            val aOrdinal = topLevelOrdinal(a)
            val bOrdinal = topLevelOrdinal(b)
            if (aOrdinal != null && bOrdinal != null) {
                aOrdinal - bOrdinal
            } else {
                platformComparator.compare(a, b)
            }
        }
    }

    /** 顶层条目（正常/缺失文件夹节点）在 folders 数组中的序号；非顶层条目返回 null。 */
    private fun topLevelOrdinal(node: NodeDescriptor<*>): Int? = when (node) {
        is MrWorkspaceTopFolderNode -> node.ordinal
        is MrWorkspaceMissingFolderNode -> node.ordinal
        else -> null
    }

    /**
     * [MrWorkspaceViewPane] 组件所用的数据结构嘞。
     */
    private inner class MrWorkspaceTreeStructure : ProjectTreeStructure(myProject, ID) {
        override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
            MrWorkspaceRootNode(project, settings)
    }

    /**
     * [MrWorkspaceViewPane] 组件 根节点。
     */
    private inner class MrWorkspaceRootNode(
        project: Project,
        settings: ViewSettings,
    ) : ProjectViewNode<Project>(project, project, settings) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> {
            val loaded = try {
                loadWorkspace(myProject, getMrWorkspaceSettings(myProject).state.selectedWorkspaceFile)
            } catch (e: MrWorkspaceUnusableException) {
                LOG.warn("All *.code-workspace files are unusable: ${e.message}")
                workspaceRootDirectories = emptyList()
                showEmptyText(NavifoxMessageBundle.message("MrWorkspaceViewPane.unusable"))
                return emptyList()
            }
            if (loaded == null) {
                workspaceRootDirectories = emptyList()
                showEmptyText(NavifoxMessageBundle.message("MrWorkspaceViewPane.empty"))
                return emptyList()
            }
            workspaceRootDirectories = loaded.workspace.folders.mapNotNull { it.directory }
            notifyIfAutoPicked(loaded.candidates, loaded.workspace.file)
            return folderNodes(settings, loaded.workspace)
        }

        override fun update(presentation: PresentationData) {
            presentation.setIcon(AllIcons.Nodes.Workspace)
            presentation.setPresentableText(NavifoxMessageBundle.message("MrWorkspaceViewPane.title"))
        }

        /**
         * 本视图可能包含 [file] 当且仅当它位于当前工作区某个根目录下（或即根目录本身）。
         * 平台“在此视图中选择”的路径遍历依赖此判断决定是否深入子树。
         */
        override fun contains(file: VirtualFile): Boolean =
            workspaceRootDirectories.any { root -> root === file || VfsUtilCore.isAncestor(root, file, true) }
    }

    /**
     * [MrWorkspaceViewPane] 组件 顶层文件夹节点。
     *
     * - 显示 `*.code-workspace` 文件中的 `folders[].name`，如果没有则提取 `folders[].path` 的目录名称；
     *   标题第二行（location）显示该 folder 相对项目根的路径。
     * - 子节点是“内容根去重 + 空骨架叶子化”的可见树（[MrVisibleDirectoryNode]），不是平台原始目录树。
     */
    private class MrWorkspaceTopFolderNode(
        project: Project,
        directory: PsiDirectory,
        settings: ViewSettings,
        private val displayName: String,
        private val locationText: String?,
        private val otherRootPaths: Set<String>,
        val ordinal: Int,
    ) : ProjectViewNode<PsiDirectory>(project, directory, settings) {

        // 委托给谁。
        // FUTURE: 有没有更好的写法呢？
        private val delegate = PsiDirectoryNode(project, directory, settings)
        private val visibleTree = MrVisibleDirectoryNode(project, directory, settings, otherRootPaths)

        override fun getChildren(): Collection<AbstractTreeNode<*>> = visibleTree.children

        override fun update(presentation: PresentationData) {
            presentation.setIcon(AllIcons.Nodes.Folder)
            presentation.setPresentableText(displayName)
            if (locationText != null) {
                presentation.setLocationString(locationText)
            }
        }

        override fun contains(file: VirtualFile): Boolean = delegate.contains(file)
    }

    /**
     * 目录节点包装：把平台目录树（[PsiDirectoryNode]）按“内容根去重”过滤后逐层重排——
     *
     * - 子目录若等于其它 workspace folder 根（[otherRootPaths]），整棵不再展示（内容归属其自己的顶层）；
     * - 文件等叶子节点直接沿用平台节点；
     * - 自身实现 [LeafState.Supplier]：可见子项为空（如“空骨架”目录）时返回 [LeafState.ALWAYS]，
     *   树不显示展开箭头但目录节点保留。
     *
     * 排序与包含等钩子全部委托给内部 [PsiDirectoryNode]，行为与原生一致。
     */
    private class MrVisibleDirectoryNode(
        project: Project,
        directory: PsiDirectory,
        settings: ViewSettings,
        private val otherRootPaths: Set<String>,
    ) : ProjectViewNode<PsiDirectory>(project, directory, settings), LeafState.Supplier {

        private val delegate = PsiDirectoryNode(project, directory, settings)

        private var cachedChildren: List<AbstractTreeNode<*>>? = null

        private fun computeChildren(): List<AbstractTreeNode<*>> {
            cachedChildren?.let { return it }
            val result = ArrayList<AbstractTreeNode<*>>()
            val children = delegate.children
            if (children.isEmpty()) {
                // 平台对项目外的目录不产子项（依赖项目索引）：改用 PSI 直接列出，保证任意路径的
                // 外部 folder 也可浏览。
                for (sub in value.subdirectories) {
                    if (sub.virtualFile.path in otherRootPaths) continue
                    result.add(MrVisibleDirectoryNode(this.project, sub, settings, otherRootPaths))
                }
                for (file in value.files) {
                    result.add(PsiFileNode(this.project, file, settings))
                }
            } else {
                for (child in children) {
                    if (child is PsiDirectoryNode) {
                        val dir = child.value ?: continue
                        val vf = dir.virtualFile
                        if (vf.path in otherRootPaths) continue // 整棵被其它工作区文件夹覆盖
                        result.add(MrVisibleDirectoryNode(child.project, dir, settings, otherRootPaths))
                    } else {
                        result.add(child)
                    }
                }
            }
            cachedChildren = result
            return result
        }

        override fun getChildren(): Collection<AbstractTreeNode<*>> = computeChildren()

        override fun getLeafState(): LeafState {
            cachedChildren?.let { return if (it.isEmpty()) LeafState.ALWAYS else LeafState.NEVER }
            return LeafState.ASYNC
        }

        // 外观委托给平台目录节点：目录名、图标、颜色等与原生一致。
        override fun update(presentation: PresentationData) {
            delegate.update(presentation)
        }

        // 排序与包含等行为与原生 PsiDirectoryNode 保持一致。
        override fun getSortOrder(settings: NodeSortSettings): NodeSortOrder = delegate.getSortOrder(settings)
        override fun getManualOrderKey(): Comparable<*>? = delegate.getManualOrderKey()
        override fun getTypeSortWeight(sortByType: Boolean): Int = delegate.getTypeSortWeight(sortByType)
        override fun getSortKey(): Comparable<*>? = delegate.getSortKey()
        override fun getTypeSortKey(): Comparable<*>? = delegate.getTypeSortKey()
        override fun getTimeSortKey(): Comparable<*>? = delegate.getTimeSortKey()

        override fun contains(file: VirtualFile): Boolean = delegate.contains(file)
    }

    /**
     * [MrWorkspaceViewPane] 组件 顶层“目录不存在或无法解析”的文件夹节点。
     *
     * path 指向的目录不存在时仍显示一个顶层文件夹（文件夹图标叠加右下角警示角标，不可展开），
     * 标题优先用 folders 的 name，否则取 path 末尾的目录名。
     */
    private class MrWorkspaceMissingFolderNode(
        project: Project,
        settings: ViewSettings,
        private val displayName: String,
        private val locationText: String?,
        val ordinal: Int,
    ) : ProjectViewNode<String>(project, displayName, settings) {

        companion object {
            // 文件夹图标 + 警示角标（同尺寸图层直接叠加，不缩放）。
            private val FOLDER_WITH_MARK: Icon by lazy {
                LayeredIcon.layeredIcon(
                    arrayOf(AllIcons.Nodes.Folder, AllIcons.Nodes.ErrorMark)
                )
            }
        }

        override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

        override fun update(presentation: PresentationData) {
            presentation.setIcon(FOLDER_WITH_MARK)
            presentation.setPresentableText(displayName)
            if (locationText != null) {
                presentation.setLocationString(locationText)
            }
        }

        override fun contains(file: VirtualFile): Boolean = false
    }

    /**
     * 把解析好的文件夹列表转成树节点。
     *
     * - 空工作区（没有声明 folders）时树没有子节点，由 [showEmptyText] 在树中央给出占位提示。
     * - 内容根去重：同一物理文件只在其“最深所属”的 workspace folder 下展示 —— 任一顶层 folder
     *   的子树中，凡目录等于另一个 folder 根（[rootFilter]），该整棵子树都不再渲染。
     * - 与 VS Code 一致：相同 path 的 folder 只保留首个；path 不存在或无法解析时仍显示顶层节点
     *   （[MrWorkspaceMissingFolderNode]，灰显 + 错误图标），而不是错误行。
     */
    private fun folderNodes(settings: ViewSettings, workspace: MrWorkspace): List<AbstractTreeNode<*>> {
        if (workspace.folders.isEmpty()) {
            showEmptyText(NavifoxMessageBundle.message("MrWorkspaceViewPane.noFolders"))
            return emptyList()
        }
        showEmptyText(null)
        val psiManager = PsiManager.getInstance(myProject)
        // 相同目录（按解析后的真实路径比较，与 VS Code 一致）只保留首个声明。
        val workspacePath = workspace.file.path
        val seen = HashSet<String>()
        val uniqueFolders = workspace.folders.filter { folder -> seen.add(folderIdentityKey(workspacePath, folder.path)) }
        // 所有工作区文件夹根目录的路径集合；作为子目录出现时视为“已被其它 folder 拥有”，整棵剪除。
        val otherRootPaths = uniqueFolders.mapNotNull { it.directory?.path }.toSet()
        return uniqueFolders.mapIndexed { index, folder ->
            val directory = folder.directory
            if (directory == null) {
                // path 不存在：仍显示顶层文件夹（文件夹图标叠警示角标），标题用 name 或目录名回退。
                MrWorkspaceMissingFolderNode(
                    myProject,
                    settings,
                    folder.name ?: folderPathBasename(folder.path),
                    normalizedPathText(folder.path),
                    index,
                )
            } else {
                val psiDirectory = psiManager.findDirectory(directory)
                if (psiDirectory != null) {
                    // 显示名优先取 workspace 中的 name，否则回退到目录名；顺序保持 folders 数组顺序。
                    val displayName = folder.name ?: psiDirectory.name
                    MrWorkspaceTopFolderNode(
                        myProject,
                        psiDirectory,
                        settings,
                        displayName,
                        relativeLocationText(directory),
                        otherRootPaths,
                        index,
                    )
                } else {
                    MrWorkspaceMissingFolderNode(
                        myProject,
                        settings,
                        folder.name ?: folderPathBasename(folder.path),
                        normalizedPathText(folder.path),
                        index,
                    )
                }
            }
        }
    }

    /** 相同 path 的去重键：反斜杠归一为正斜杠、去掉前导 `./` 与尾部斜杠。 */
    private fun normalizeFolderPathKey(path: String): String =
        path.replace('\\', '/').removePrefix("./").removeSuffix("/")

    /**
     * folder 的去重键：优先用“解析后的真实目录路径”（相对 workspace 文件解析并归一化），使
     * `../navifox-pages/apps/hei` 与 `./apps/hei` 这类指向同一目录的写法合并；无法解析时退回
     * 字符串规范化键。
     */
    private fun folderIdentityKey(workspaceFilePath: String, path: String): String {
        val resolved = resolveFolderPath(workspaceFilePath, path)
        return if (resolved != null) {
            resolved.toPath().normalize().toAbsolutePath().toString().replace('\\', '/')
        } else {
            normalizeFolderPathKey(path)
        }
    }

    /** 用于展示的路径文本：反斜杠归一、去掉前导 `./` 与尾部斜杠；根（`.` 或空）返回 null。 */
    private fun normalizedPathText(path: String): String? {
        val normalized = normalizeFolderPathKey(path)
        return normalized.takeIf { it.isNotEmpty() && it != "." }
    }

    /** 从 path 中解析目录名（不依赖文件系统，用于目录不存在时的标题回退）。 */
    private fun folderPathBasename(path: String): String {
        val trimmed = normalizeFolderPathKey(path).trimEnd('/').ifEmpty { return path }
        val last = trimmed.substringAfterLast('/').ifEmpty { trimmed }
        return last.takeIf { it.isNotEmpty() && it != "." } ?: path
    }

    /**
     * 顶层标题第二行展示的路径文本：以项目根（深度 0）为基准 —— 项目内显示相对路径；
     * 项目外（上级目录）为避免前置一串 `../`，直接显示绝对路径；与根重合时返回 null 不显示。
     */
    private fun relativeLocationText(directory: VirtualFile): String? {
        val base = myProject.basePath ?: return normalizedPathText(directory.path)
        return try {
            val basePath = java.nio.file.Paths.get(base)
            val dirPath = java.nio.file.Paths.get(directory.path)
            val relative = basePath.relativize(dirPath).toString().replace('\\', '/')
            when {
                relative.isEmpty() || relative == "." -> null
                relative == ".." || relative.startsWith("../") -> directory.path.replace('\\', '/')
                else -> relative
            }
        } catch (e: Exception) {
            normalizedPathText(directory.path)
        }
    }

    /**
     * 树没有内容时，在面板中央显示一段占位文本（类似于“结构”“通知”等工具窗口的空态）。
     *
     * @param message 占位文本；传 null 表示清除。树一旦出现子节点，平台本身也不会绘制空文本。
     */
    private fun showEmptyText(message: String?) {
        fun apply() {
            if (myProject.isDisposed) return
            val emptyText = (getTree() as? Tree)?.emptyText ?: return
            if (message == null) emptyText.clear() else emptyText.setText(message)
        }
        if (SwingUtilities.isEventDispatchThread()) {
            apply()
        } else {
            SwingUtilities.invokeLater { apply() }
        }
    }

    /** 多个文件且未在设置中指定时自动选用一个（解析失败会顺延），弹一次右下角气泡并可跳转设置页。 */
    private fun notifyIfAutoPicked(files: List<VirtualFile>, chosen: VirtualFile) {
        if (files.size <= 1) return
        if (getMrWorkspaceSettings(myProject).state.selectedWorkspaceFile != null) return
        val key = files.joinToString("|") { it.name }
        if (autoPickNotifiedKey == key) return
        autoPickNotifiedKey = key

        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
        val notification = group.createNotification(
            NavifoxMessageBundle.message("MrWorkspaceViewPane.notification.title"),
            NavifoxMessageBundle.message("MrWorkspaceViewPane.notification.content", files.size, chosen.name),
            NotificationType.INFORMATION,
        )
        notification.addAction(
            NotificationAction.createSimple(
                NavifoxMessageBundle.message("MrWorkspaceViewPane.notification.action.settings")
            ) {
                if (!myProject.isDisposed) {
                    openWorkspaceSettings(myProject)
                }
            })
        Notifications.Bus.notify(notification, myProject)
    }
}

/** 供设置页在 apply 后触发当前项目里所有已创建面板刷新。 */
internal object MrWorkspacePanes {
    private val panes = CopyOnWriteArrayList<MrWorkspaceViewPane>()

    fun register(pane: MrWorkspaceViewPane) {
        if (!panes.contains(pane)) {
            panes.add(pane)
        }
    }

    fun unregister(pane: MrWorkspaceViewPane) {
        panes.remove(pane)
    }

    fun refresh(project: Project) {
        panes.forEach { if (it belongsTo project) it.updateFromRoot(true) }
    }
}

/** 同一批文件只弹一次自动选择提醒，避免刷新时反复打扰。 */
@Volatile
private var autoPickNotifiedKey: String? = null

/** 全部候选 *.code-workspace 均不可用时的诊断日志。 */
private val LOG = Logger.getInstance(MrWorkspaceViewPane::class.java)
