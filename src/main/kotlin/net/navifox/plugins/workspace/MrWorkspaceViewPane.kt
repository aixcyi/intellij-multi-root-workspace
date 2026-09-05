package net.navifox.plugins.workspace

import com.intellij.icons.AllIcons
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.dnd.aware.DnDAwareTree
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.AbstractProjectViewPaneWithAsyncSupport
import com.intellij.ide.projectView.impl.ProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import net.navifox.plugins.NavifoxMessageBundle
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
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

    // `SelectInTarget` 用于提供 Alt + F1 快捷键的功能。
    // TODO: 以后探索一下怎么提供这个功能。
    override fun createSelectInTarget(): SelectInTarget = object : SelectInTarget {

        // 在菜单里显示的代表自己的文本，所以直接用自身的 `title` 就好了。
        override fun toString(): String = title

        // 用 Project View 的 ID 表示挂靠到这个下面。
        override fun getToolWindowId(): String = ToolWindowId.PROJECT_VIEW

        // 子菜单对应的 ID。
        // 虽然可以展示多个文件夹，但那是动态展示的，没办法设置下一级，所以 ID 直接用自己的。
        override fun getMinorViewId(): String = ID

        override fun canSelect(context: SelectInContext): Boolean = false

        override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
        }
    }

    // 我记得是工具栏菜单
    override fun addToolbarActions(group: DefaultActionGroup) {
        group.add(object : AnAction(AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                updateFromRoot(true)
            }
        })
    }

    /**
     * 判断其它 [MrWorkspaceViewPane] 跟当前实例属不属于同一个 [Project]。
     */
    internal infix fun belongsTo(project: Project): Boolean = myProject === project

    override fun createComparator(): Comparator<NodeDescriptor<*>> = Comparator { a, b ->
        when {
            // 按 `*.code-workspace` 文件中的 `folders` 数组成员定义顺序排序。
            a is MrWorkspaceTopFolderNode && b is MrWorkspaceTopFolderNode -> a.ordinal - b.ordinal
            // 其它按“项目”工具窗口同款排序。
            else -> compareNodeForProjectView(a, b)
        }
    }

    // TODO: 到时候要适配“项目”工具窗口菜单的“排序依据”菜单。
    private fun compareNodeForProjectView(a: NodeDescriptor<*>, b: NodeDescriptor<*>): Int {
        val aNode = a as? AbstractTreeNode<*>
        val bNode = b as? AbstractTreeNode<*>
        val aIsDir = aNode?.isDirectory() ?: false
        val bIsDir = bNode?.isDirectory() ?: false
        if (aIsDir != bIsDir) {
            return if (aIsDir) -1 else 1
        }
        val aName = aNode?.presentation?.presentableText?.lowercase() ?: ""
        val bName = bNode?.presentation?.presentableText?.lowercase() ?: ""
        return aName.compareTo(bName)
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
            val files = findWorkspaceFiles(myProject)
            val chosen = resolveWorkspaceFile(myProject, files) ?: return listOf(
                MrWorkspaceMessageNode(
                    settings,
                    NavifoxMessageBundle.message("MrWorkspaceViewPane.empty"),
                    isError = true
                )
            )
            notifyIfAutoPicked(files, chosen)
            return folderNodes(settings, parseWorkspace(chosen))
        }

        override fun update(presentation: PresentationData) {
            presentation.setIcon(AllIcons.Nodes.Workspace)
            presentation.setPresentableText(NavifoxMessageBundle.message("MrWorkspaceViewPane.title"))
        }

        override fun contains(file: VirtualFile): Boolean = false
    }

    /**
     * [MrWorkspaceViewPane] 组件 顶层文件夹节点。
     *
     * - 显示 `*.code-workspace` 文件中的 `folders[].name`，如果没有则提取 `folders[].path` 的目录名称。
     * - 子节点递归内容委托给 [PsiDirectoryNode] 来保持一致的行为。
     */
    private class MrWorkspaceTopFolderNode(
        project: Project,
        directory: PsiDirectory,
        settings: ViewSettings,
        private val displayName: String,
        val ordinal: Int,
    ) : ProjectViewNode<PsiDirectory>(project, directory, settings) {

        // 委托给谁。
        // FUTURE: 有没有更好的写法呢？
        private val delegate = PsiDirectoryNode(project, directory, settings)

        override fun getChildren(): Collection<AbstractTreeNode<*>> = delegate.children

        override fun update(presentation: PresentationData) {
            presentation.setIcon(AllIcons.Nodes.Folder)
            presentation.setPresentableText(displayName)
        }

        override fun contains(file: VirtualFile): Boolean = delegate.contains(file)
    }

    /**
     * [MrWorkspaceViewPane] 组件 纯文本节点。
     *
     * - 无法展示为目录时的占位/错误行。
     */
    private inner class MrWorkspaceMessageNode(
        settings: ViewSettings,
        text: String,
        private val isError: Boolean,
    ) : ProjectViewNode<String>(myProject, text, settings) {

        override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

        override fun update(presentation: PresentationData) {
            presentation.setPresentableText(value)
            presentation.setIcon(if (isError) AllIcons.Nodes.ErrorMark else null)
        }

        override fun contains(file: VirtualFile): Boolean = false
    }

    /**
     * 单个文件夹。
     *
     * - 详见 Visual Studio Code 的
     *   [Workspace file schema](https://code.visualstudio.com/docs/editing/workspaces/multi-root-workspaces#_workspace-file-schema)。
     *
     * @param name 文件夹名称。不填的时候默认为 [path] 的目录名称。
     * @param path 文件夹路径。可以是相对于 `*.code-workspace` 所在的目录，也可以是绝对路径。
     * @param directory 可以直接使用的 [VirtualFile] 实例。
     */
    private data class MrFolder(val name: String?, val path: String, val directory: VirtualFile?)

    /**
     * 单个工作区。
     *
     * @param folders 零个或多个文件夹。
     * @param parseError 解析错误。
     */
    private data class MrWorkspace(val folders: List<MrFolder>, val parseError: String?)

    private fun folderNodes(settings: ViewSettings, parsed: MrWorkspace): List<AbstractTreeNode<*>> {
        val error = parsed.parseError
        if (error != null) {
            return listOf(MrWorkspaceMessageNode(settings, error, isError = true))
        }
        if (parsed.folders.isEmpty()) {
            return listOf(
                MrWorkspaceMessageNode(
                    settings,
                    NavifoxMessageBundle.message("MrWorkspaceViewPane.noFolders"),
                    isError = false
                )
            )
        }
        val psiManager = PsiManager.getInstance(myProject)
        return parsed.folders.mapIndexed { index, folder ->
            val directory = folder.directory
            val psiDirectory: PsiDirectory? = if (directory == null) null else psiManager.findDirectory(directory)
            if (psiDirectory != null) {
                // 显示名优先取 workspace 中的 name,否则回退到目录名;顺序保持 folders 数组顺序。
                val displayName = folder.name ?: psiDirectory.name
                MrWorkspaceTopFolderNode(myProject, psiDirectory, settings, displayName, index)
            } else {
                MrWorkspaceMessageNode(
                    settings,
                    NavifoxMessageBundle.message("MrWorkspaceViewPane.folderNotFound", folder.path),
                    isError = true
                )
            }
        }
    }

    private fun parseWorkspace(file: VirtualFile): MrWorkspace {
        val text = try {
            file.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return MrWorkspace(
                emptyList(),
                NavifoxMessageBundle.message("MrWorkspaceViewPane.errorRead", file.name, e.message ?: "")
            )
        }
        val root: Map<String, Any?> = try {
            JsonReaderEx(Jsonc.sanitize(text)).use { JsonUtil.nextObject(it) }
        } catch (e: Exception) {
            return MrWorkspace(
                emptyList(),
                NavifoxMessageBundle.message("MrWorkspaceViewPane.errorJsonc", file.name, e.message ?: "")
            )
        }
        val rawFolders = root["folders"] ?: return MrWorkspace(emptyList(), null)
        if (rawFolders !is List<*>) {
            return MrWorkspace(
                emptyList(),
                NavifoxMessageBundle.message("MrWorkspaceViewPane.errorFoldersType", file.name)
            )
        }
        val folders = rawFolders.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val path = map["path"] as? String ?: return@mapNotNull null
            MrFolder(map["name"] as? String, path, resolveFolder(file, path))
        }
        return MrWorkspace(folders, null)
    }

    /** VS Code 中相对 path 以 .code-workspace 文件所在目录为基准。 */
    private fun resolveFolder(workspaceFile: VirtualFile, path: String): VirtualFile? {
        val io = File(path)
        val target = if (io.isAbsolute) {
            io
        } else {
            File(File(workspaceFile.path).parentFile, path)
        }
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(target)
    }

    /** 多个文件且未在设置中指定时自动取了第一个,弹一次右下角气泡并可跳转设置页。 */
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

/** 同一批文件只弹一次自动选择提醒,避免刷新时反复打扰。 */
@Volatile
private var autoPickNotifiedKey: String? = null

/**
 * 判断某个树节点是不是目录节点。
 */
private fun AbstractTreeNode<*>.isDirectory(): Boolean = when (value) {
    is PsiDirectory -> true
    is PsiFile -> false
    is VirtualFile -> (value as VirtualFile).isDirectory
    else -> false
}
