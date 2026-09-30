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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.messages.MessageBusConnection
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.tree.LeafState
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.MrFolder
import net.navifox.plugins.core.MrWorkspaceUnusableException
import net.navifox.plugins.core.WORKSPACE_SUFFIX
import net.navifox.plugins.core.loadWorkspace
import net.navifox.plugins.core.resolveFolderPath
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.LayoutManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.Timer
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
class MrWorkspacePane(project: Project) : AbstractProjectViewPaneWithAsyncSupport(project) {

    companion object {
        const val ID = "MrWorkspacePane"
        const val NOTIFICATION_GROUP_ID = "MrWorkspace"
    }

    /** 包装组件（树 + 空态覆盖层）。平台每次切换回本面板都会再次调用 [createComponent]，必须缓存复用。 */
    private var contentWrapper: JComponent? = null

    /** 空态覆盖层：无可用配置时显示在树上方；树始终保留在组件树里以保证 `isShowing`（工具窗头部菜单等依赖它）。 */
    private var noConfigOverlay: JPanel? = null

    /** 空态创建按钮（用于显示期间设为窗口默认按钮以呈现“OK/确认”强调样式）。 */
    private var configButton: JButton? = null

    /** 设为默认按钮前保存的原默认按钮（切回树/面板隐藏时还原）。 */
    private var previousDefaultButton: JButton? = null

    /** 当前被我们占用默认按钮的根窗格。 */
    private var activeRootPane: JRootPane? = null

    /** VFS 订阅（`VirtualFileManager.VFS_CHANGES` 话题）：原生“重构”、新建/删除/移动/重命名等变化驱动自动刷新。 */
    private var vfsBusConnection: MessageBusConnection? = null

    /** 自动刷新防抖计时器：一批 VFS 事件只触发一次整树刷新，避免重构等批量操作反复重载。 */
    private var autoRefreshTimer: Timer? = null

    /** 项目根目录路径（正斜杠、去尾斜杠），用于识别“项目根第一层的 *.code-workspace”配置变化。 */
    private val projectBaseDirPath: String? by lazy {
        myProject.basePath?.replace('\\', '/')?.trimEnd('/')
    }

    /**
     * “当前工作区路径”（项目根目录）的绝对规范化路径键，供“自动隐藏当前工作区目录”比对。
     *
     * 先把 `basePath`（IDE 上报的路径格式不定，可能是 `C:\…` 或 `C:/…`）转成 [java.io.File]
     * 再取绝对规范化路径，避免拿 `.`、`./` 这类写法直接做字符串比较。
     */
    private val projectBasePathKey: String? by lazy {
        val base = myProject.basePath ?: return@lazy null
        absolutePathKey(File(base))
    }

    override fun getTitle(): String = NavifoxMessageBundle.message("MrWorkspacePane.title")

    override fun getId(): String = ID

    override fun getIcon(): Icon = AllIcons.Nodes.Workspace

    override fun getWeight(): Int = 1

    override fun createComponent(): JComponent {
        val cached = contentWrapper
        if (cached != null) {
            // 平台切回面板会再次调用 createComponent：基类树组件已缓存、不会再自动加载，
            // 这里主动触发一次“扫描 + 空态判定/重载”（否则要手点“刷新”才恢复提示）。
            SwingUtilities.updateComponentTreeUI(cached)
            SwingUtilities.invokeLater {
                if (!myProject.isDisposed) {
                    updateFromRoot(true)
                }
            }
            return cached
        }
        val treeContent = super.createComponent()
        MrWorkspacePanes.register(this)
        installAutoRefresh()

        val overlay = createNoConfigPanel().apply { isVisible = false }
        noConfigOverlay = overlay

        // JLayeredPane 显式分层：树在 DEFAULT 层、空态覆盖层在 PALETTE 层，
        // 保证覆盖层一定绘制在树之上（普通容器的兄弟叠放顺序不可靠）。
        val wrapper = JLayeredPane().apply {
            layout = FillOverlayLayout
            add(treeContent, JLayeredPane.DEFAULT_LAYER)
            add(overlay, JLayeredPane.PALETTE_LAYER)
            // 显式分层并置顶；覆盖层还需“不透明 + 自绘背景”才会在顶层被真正绘制
            setLayer(overlay, JLayeredPane.PALETTE_LAYER)
            moveToFront(overlay)
            addComponentListener(object : ComponentAdapter() {
                override fun componentShown(e: ComponentEvent) {
                    applyDefaultButtonIfVisible()
                }

                override fun componentHidden(e: ComponentEvent) {
                    releaseDefaultButton()
                }
            })
        }
        contentWrapper = wrapper
        return wrapper
    }

    /**
     * 无可用配置（无文件 / 全部解析失败）时显示空态覆盖层（树仍保持可见/`isShowing`），
     * 有可展示内容时隐藏。未构建完成前调用是安全的。
     *
     * 平台没有公开的“主按钮/强调按钮”样式开关：对话框“确认/OK”之所以是强调色，
     * 是因为它是所在窗口的**默认按钮**（JRootPane.defaultButton，LAF 只给默认按钮画强调）。
     * 因此空态显示期间把创建按钮临时设为窗口默认按钮（Enter 可直接触发创建，
     * 语义上正是空态的“主操作”）；隐藏/面板切换走时还原。
     */
    private fun setNoConfigStateVisible(noConfig: Boolean) {
        fun apply() {
            if (myProject.isDisposed) return
            val overlay = noConfigOverlay ?: return
            overlay.isVisible = noConfig
            if (noConfig) {
                applyDefaultButtonIfVisible()
            } else {
                releaseDefaultButton()
            }
            val wrapper = contentWrapper
            wrapper?.revalidate()
            wrapper?.repaint()
            if (noConfig) {
                overlay.validate()
                wrapper?.validate()
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            apply()
        } else {
            SwingUtilities.invokeLater { apply() }
        }
    }

    /** 空态覆盖层可见且已挂到窗口时，把创建按钮设为窗口默认按钮。 */
    private fun applyDefaultButtonIfVisible() {
        val overlay = noConfigOverlay ?: return
        val button = configButton ?: return
        if (!overlay.isVisible) return
        val rootPane = SwingUtilities.getRootPane(overlay) ?: return
        if (rootPane.defaultButton === button) return
        previousDefaultButton = rootPane.defaultButton
        activeRootPane = rootPane
        rootPane.defaultButton = button
        button.repaint()
    }

    /** 还原默认按钮（空态隐藏 / 面板不再显示 / 销毁时）。 */
    private fun releaseDefaultButton() {
        val root = activeRootPane ?: return
        val button = configButton
        activeRootPane = null
        if (button != null && root.defaultButton === button) {
            root.defaultButton = previousDefaultButton
        }
        previousDefaultButton = null
    }

    /**
     * 空态覆盖层内容：标题与按钮各自水平居中（Swing BoxLayout 居中——DSL 行布局无法逐行居中）。
     * 创建按钮点击走 [createWorkspaceConfig]（保存对话框先行），成功后刷新自动隐藏覆盖层。
     */
    private fun createNoConfigPanel(): JPanel {
        val box = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel(NavifoxMessageBundle.message("MrWorkspacePane.noConfigState.title")).apply {
                alignmentX = Component.CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(12))
            add(JButton(NavifoxMessageBundle.message("MrWorkspacePane.noConfigState.create")).apply {
                alignmentX = Component.CENTER_ALIGNMENT
                addActionListener {
                    if (!myProject.isDisposed) {
                        createWorkspaceConfig(myProject)
                    }
                }
                configButton = this
            })
        }
        return JPanel(GridBagLayout()).apply {
            // 覆盖层必须不透明（自绘背景）才会在 JLayeredPane 上层正常绘制
            isOpaque = true
            background = UIUtil.getTreeBackground()
            add(box) // 默认约束即居中
        }
    }

    override fun dispose() {
        releaseDefaultButton()
        MrWorkspacePanes.unregister(this)
        autoRefreshTimer?.stop()
        autoRefreshTimer = null
        vfsBusConnection?.disconnect()
        vfsBusConnection = null
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

    /**
     * 最近一次构建的“文件夹”菜单选项（按 `folders` 顺序，只含路径能解析的文件夹）。
     *
     * 供 [MrWorkspaceFoldersMenu] 列出可选文件夹；面板还没构建出树时为空列表。
     */
    @Volatile
    private var actionOrder: List<MrFolderLeaf> = emptyList()

    /** “文件夹”菜单的可选项（面板尚未构建时为空列表）。 */
    internal fun getFolderMenuLeaves(): List<MrFolderLeaf> = actionOrder

    /** 当前**实际渲染**的顶层文件夹选择键（供菜单标注“显示所有”的当前状态）。 */
    internal fun getVisibleFolderKeys(): Set<String> = getMrWorkspaceFolderFilter(myProject).visibleFolderKeys

    /** 本面板所属项目（[MrWorkspaceFoldersMenuAction] 等外部入口需要用它来定位面板与刷新）。 */
    internal fun project(): Project = myProject

    /**
     * 按选择键定位并展开顶层文件夹（“文件夹”菜单选中单项后调用）。
     *
     * 走平台自己的定位链路（[selectWithCallback] ＋ `VirtualFile`）：树会在必要时展开沿途节点、滚动到该行并选中它。
     * 选择键对应的目录已被隐藏或消失时静默跳过（面板已经退回“显示所有”）。
     */
    internal fun expandTopFolder(folderKey: String) {
        if (myProject.isDisposed) return
        val directory = actionOrder.firstOrNull { it.key == folderKey }?.directory ?: return
        selectWithCallback(directory, directory, false)
    }

    // 我记得是工具栏菜单
    override fun addToolbarActions(group: DefaultActionGroup) {
        // “文件夹”子菜单排在“刷新”前面：展开后选一个顶层文件夹只看它，或选“显示所有文件夹”恢复。
        group.add(MrWorkspaceFoldersMenuAction())
        group.add(object : AnAction(
            NavifoxMessageBundle.message("MrWorkspacePane.refresh"),
            NavifoxMessageBundle.message("MrWorkspacePane.refresh.description"),
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                updateFromRoot(true)
            }
        })
    }

    /**
     * 判断其它 [MrWorkspacePane] 跟当前实例属不属于同一个 [Project]。
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
     * [MrWorkspacePane] 组件所用的数据结构嘞。
     */
    private inner class MrWorkspaceTreeStructure : ProjectTreeStructure(myProject, ID) {
        override fun createRoot(project: Project, settings: ViewSettings): AbstractTreeNode<*> =
            MrWorkspaceRootNode(project, settings)
    }

    /**
     * [MrWorkspacePane] 组件 根节点。
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
                getMrWorkspaceFolderFilter(myProject).setVisibleKeys(emptySet())
                setNoConfigStateVisible(true)
                return emptyList()
            }
            if (loaded == null) {
                workspaceRootDirectories = emptyList()
                getMrWorkspaceFolderFilter(myProject).setVisibleKeys(emptySet())
                setNoConfigStateVisible(true)
                return emptyList()
            }
            setNoConfigStateVisible(false)
            // “强制显示当前工作区目录”：配置文件没声明时补一个到尾部，与显式声明一视同仁。
            val folders = effectiveFolders(loaded.workspace.file.path, loaded.workspace.folders)
            workspaceRootDirectories = folders.mapNotNull { it.directory }
            notifyIfAutoPicked(loaded.candidates, loaded.workspace.file)

            // 可展示的顶层文件夹（含“自动隐藏当前工作区目录”与“相同 path 只保留首个”）：树与菜单的唯一真相。
            val canonical = visibleFolderLeaves(loaded.workspace.file.path, folders)
            // “文件夹”菜单（Alt＋F2）的可选项必须与树同源，否则会列出已被隐藏的目录
            // （2026-09-30 实测：勾选“自动隐藏当前工作区目录”后，菜单里仍能看到那个目录）。
            // 路径无法解析的 folder 没有可比较的目录身份，因此不进菜单（但树里照常显示警示节点）。
            actionOrder = canonical.filter { it.resolved != null }
            val filter = getMrWorkspaceFolderFilter(myProject)
            filter.setVisibleKeys(canonical.map { it.key }.toSet())
            if (canonical.isEmpty()) {
                // 空工作区，或所有 folder 都被“自动隐藏当前工作区目录”隐藏。
                showEmptyText(
                    if (folders.isEmpty()) {
                        NavifoxMessageBundle.message("MrWorkspacePane.noFolders")
                    } else {
                        NavifoxMessageBundle.message("MrWorkspacePane.allFoldersHidden")
                    }
                )
                return emptyList()
            }
            // 过滤（“文件夹”菜单选中一个）在**已渲染**的文件夹上生效；目标文件夹已被设置隐藏时自动退回“显示所有”。
            val selectedKey = filter.selectedFolderKey
            val matchIndex = canonical.indexOfFirst { it.key == selectedKey }
            val visible = if (matchIndex < 0) canonical else listOf(canonical[matchIndex])
            showEmptyText(null)
            // 过滤到单个文件夹时**解除内容根去重**：该文件夹的整棵子树都可见，不会留下被剪空的骨架目录。
            val filtering = visible.size == 1 && canonical.size > 1
            val pruningRoots = if (filtering) {
                emptySet()
            } else {
                visible.mapNotNull { it.resolved?.let(::folderSelectionKeyOf) }.toSet()
            }
            return folderNodes(settings, visible, pruningRoots)
        }

        override fun update(presentation: PresentationData) {
            presentation.setIcon(AllIcons.Nodes.Workspace)
            presentation.setPresentableText(NavifoxMessageBundle.message("MrWorkspacePane.title"))
        }

        /**
         * 本视图可能包含 [file] 当且仅当它位于当前工作区某个根目录下（或即根目录本身）。
         * 平台“在此视图中选择”的路径遍历依赖此判断决定是否深入子树。
         */
        override fun contains(file: VirtualFile): Boolean =
            workspaceRootDirectories.any { root -> root == file || VfsUtilCore.isAncestor(root, file, true) }
    }

    /**
     * 顶层文件夹的全部展示信息；同时充当“文件夹”菜单（Alt＋F2）里的一项。
     *
     * @param key 选择键（[folderSelectionKeyOf]，正斜杠绝对规范化路径）：既是菜单项的标识，也是过滤依据。
     * @param resolved 解析后的真实路径（`File`）；path 指向的目录不存在或无法解析时为空。
     * @param directory 解析后的 [VirtualFile]；没有解析或 VFS 里找不到时为空（树里显示警示节点）。
     * @param displayName 显示名：优先 `folders[].name`，否则目录名。
     * @param locationText 名称后方的灰色路径文本（或“当前工作区”标记）；按“不显示文件夹所在路径”设置可以为空。
     */
    internal class MrFolderLeaf(
        val key: String,
        val resolved: File?,
        val directory: VirtualFile?,
        val displayName: String,
        val locationText: String?,
    )

    /**
     * [MrWorkspacePane] 组件 顶层文件夹节点。
     *
     * - 显示 `*.code-workspace` 文件中的 `folders[].name`，如果没有则提取 `folders[].path` 的目录名称；
     *   与该 folder 相对项目根的路径（灰色小字，跟在名称后方同一行）；若该 folder 就是当前工作区（项目根），
     *   这个位置改显示“当前工作区”标记。
     * - 子节点是“内容根去重 + 空骨架叶子化”的可见树（[MrVisibleDirectoryNode]），不是平台原始目录树。
     */
    private class MrWorkspaceTopFolderNode(
        project: Project,
        directory: PsiDirectory,
        settings: ViewSettings,
        private val leaf: MrFolderLeaf,
        /** 子目录中要整棵剪除的“已被其它顶层文件夹拥有”的路径集合；过滤到单个文件夹时为空集（解除去重）。 */
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
            presentation.setPresentableText(leaf.displayName)
            if (leaf.locationText != null) {
                presentation.setLocationString(leaf.locationText)
            }
        }
        override fun contains(file: VirtualFile): Boolean = delegate.contains(file)

        /**
         * 定位（SelectIn／随处搜索选中文件夹／自动滚动）时平台可能以 [VirtualFile] 匹配节点：
         * 平台目录节点重写了 `canRepresent` 来处理 VirtualFile，自定义包装节点必须一并委托，
         * 否则只有沿用平台的 `PsiFileNode`（文件）能命中，文件夹节点永远匹配不上而无法定位。
         */
        override fun canRepresent(element: Any?): Boolean =
            (element is VirtualFile && element == value?.virtualFile) || delegate.canRepresent(element)
    }

    /**
     * 目录节点包装：把平台目录树（[PsiDirectoryNode]）按“内容根去重”过滤后逐层重排——
     *
     * - 子目录若等于其它 workspace folder 根（[otherRootPaths]），整棵不再展示（内容归属其自己的顶层）；
     * - 文件等叶子节点直接沿用平台节点；
     * - 自身实现 [LeafState.Supplier]：可见子项为空（如“空骨架”目录）时返回 [LeafState.ALWAYS]，
     *   树不显示展开箭头但目录节点保留。
     *
     * 排序、包含与定位匹配（`canRepresent`）等钩子全部委托给内部 [PsiDirectoryNode]，行为与原生一致。
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
                    // 与 otherRootPaths 同为“正斜杠绝对路径”（VirtualFile 本就用正斜杠，这里只是防御性统一）。
                    if (sub.virtualFile.path.replace('\\', '/') in otherRootPaths) continue
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
                        // 同上：键与 VirtualFile 路径都按正斜杠比较，整棵被其它工作区文件夹覆盖时剪除。
                        if (vf.path.replace('\\', '/') in otherRootPaths) continue
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

        /** 与顶层节点同理：定位匹配必须委托内部 [PsiDirectoryNode] 才能被 VirtualFile 命中。 */
        override fun canRepresent(element: Any?): Boolean =
            (element is VirtualFile && element == value?.virtualFile) || delegate.canRepresent(element)
    }

    /**
     * [MrWorkspacePane] 组件 顶层“目录不存在或无法解析”的文件夹节点。
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
     * 实际参与渲染与定位的顶层文件夹列表。
     *
     * 勾选“强制显示当前工作区目录”且配置文件**没有**声明与项目根相同的 folder 时，在**尾部**补一个：
     * 名字回退为目录名，位置文本由“当前工作区”标记给出。补出来的条目与显式声明的完全等价——同样进入
     * [otherRootPaths] 参与内容根去重，也会计入供定位使用的 [workspaceRootDirectories]。
     */
    private fun effectiveFolders(workspacePath: String, folders: List<MrFolder>): List<MrFolder> {
        if (!getMrWorkspaceSettings(myProject).state.forceShowWorkspaceDirectory) return folders
        val basePathKey = projectBasePathKey ?: return folders
        val declared = folders.any { folder ->
            resolveFolderPath(workspacePath, folder.path)?.let { absolutePathKey(it) == basePathKey } == true
        }
        if (declared) return folders
        val base = myProject.basePath ?: return folders
        val directory = LocalFileSystem.getInstance().findFileByIoFile(File(base)) ?: return folders
        return folders + MrFolder(name = null, path = base, directory = directory)
    }

    /**
     * 当前**实际渲染**的顶层文件夹（“自动隐藏当前工作区目录”“相同 path 只保留首个”之后的可见集合）。
     *
     * 树与“文件夹”菜单（Alt＋F2）都以这份列表为准，因此二者永远一致。
     * 路径无法解析的 folder 保留在列表中（树里仍显示警示节点），但它不进“文件夹”菜单——
     * 见 [folderLeaf] 与 [MrWorkspaceFoldersMenu]。
     */
    private fun visibleFolderLeaves(workspacePath: String, folders: List<MrFolder>): List<MrFolderLeaf> {
        val settingsState = getMrWorkspaceSettings(myProject).state
        // 相同目录（按解析后的真实路径比较，与 VS Code 一致）只保留首个声明。
        val seen = HashSet<String>()
        val uniqueFolders = folders.filter { folder -> seen.add(folderIdentityKey(workspacePath, folder.path)) }
        // “自动隐藏当前工作区目录”：按解析后的绝对规范化路径与项目根目录比对（不看 `.`／`./` 写法）。
        val basePathKey = projectBasePathKey
        // “自动隐藏”与“强制显示”在设置页里互斥；若状态里两者同时为 true（手改过文件），
        // 按“强制显示”优先——此时不隐藏，必要的话还会补上缺失的当前工作区目录。
        val hideWorkspaceDirectory =
            settingsState.hideWorkspaceDirectory && !settingsState.forceShowWorkspaceDirectory
        return uniqueFolders.mapNotNull { folder ->
            val resolved = resolveFolderPath(workspacePath, folder.path)
            val hidden = hideWorkspaceDirectory && basePathKey != null && resolved != null &&
                absolutePathKey(resolved) == basePathKey
            if (hidden) null else folderLeaf(workspacePath, folder, resolved)
        }
    }

    /**
     * 把单个 folder 整理成展示信息（[visibleFolderLeaves] 去重后再调用它）。
     *
     * @param resolved 解析后的真实路径（由调用方解析后传入）。
     */
    private fun folderLeaf(
        workspacePath: String,
        folder: MrFolder,
        resolved: File?,
    ): MrFolderLeaf {
        // “不显示文件夹所在路径”：关掉时不给节点传路径文本（节点 update 里据此不设置 location）；
        // “当前工作区”标记占的是同一个位置，同样受它控制。
        val showFolderPath = getMrWorkspaceSettings(myProject).state.showFolderPath
        val directory = folder.directory
        if (directory == null) {
            // path 不存在或无法解析：仍显示顶层文件夹（文件夹图标叠警示角标），标题用 name 或目录名回退。
            // 它没有可比较的目录身份，因此不进“文件夹”菜单（[MrWorkspaceFoldersMenu] 会跳过 [resolved] 为空的项）。
            return MrFolderLeaf(
                key = resolved?.let(::folderSelectionKeyOf) ?: normalizeFolderPathKey(folder.path),
                resolved = resolved,
                directory = null,
                displayName = folder.name ?: folderPathBasename(folder.path),
                locationText = normalizedPathText(folder.path).takeIf { showFolderPath },
            )
        }
        // 与“当前工作区路径”（项目根）重合的那个 folder 没有相对路径可显示，改用“当前工作区”标记。
        // 它与普通路径文本占同一个位置，因此同样受“不显示文件夹所在路径”选项控制。
        val basePathKey = projectBasePathKey
        val currentWorkspace = basePathKey != null && absolutePathKey(File(directory.path)) == basePathKey
        val locationText = when {
            !showFolderPath -> null
            currentWorkspace -> NavifoxMessageBundle.message("MrWorkspacePane.topFolder.currentWorkspace")
            else -> relativeLocationText(directory)
        }
        return MrFolderLeaf(
            key = folderSelectionKey(directory),
            resolved = resolved,
            directory = directory,
            // 显示名优先取 workspace 中的 name，否则回退到目录名；顺序保持 folders 数组顺序。
            displayName = folder.name ?: directory.name,
            locationText = locationText,
        )
    }

    /**
     * 把（已过滤的）顶层文件夹列表转成树节点。
     *
     * - 空工作区（没有声明 folders，且“强制显示当前工作区目录”未勾选）时树没有子节点，
     *   由 [showEmptyText] 在树中央给出占位提示。
     * - 内容根去重：同一物理文件只在其“最深所属”的 workspace folder 下展示 —— 任一顶层 folder
     *   的子树中，凡目录等于另一个 folder 根（[pruningRoots]），该整棵子树都不再渲染。
     *   过滤到单个文件夹时 [pruningRoots] 为空集，即**解除去重**（只显示这一个文件夹时它应当完整可见）。
     * - 与 VS Code 一致：相同 path 的 folder 只保留首个；path 不存在或无法解析时仍显示顶层节点
     *   （[MrWorkspaceMissingFolderNode]，灰显 + 错误图标），而不是错误行。
     * - 开启“自动隐藏当前工作区目录”时，解析后的绝对路径等于项目根目录（[projectBasePathKey]）的
     *   folder 不进入可见列表，也不再充当 [pruningRoots] 中去重根的成员（其子目录因此会出现在包含它的其它根下）。
     * - [effectiveFolders] 可能已在尾部补上“当前工作区目录”，它与显式声明的条目走同一套去重、渲染与定位路径。
     */
    private fun folderNodes(
        settings: ViewSettings,
        visible: List<MrFolderLeaf>,
        pruningRoots: Set<String>,
    ): List<AbstractTreeNode<*>> {
        if (visible.isEmpty()) return emptyList()
        val psiManager = PsiManager.getInstance(myProject)
        return visible.mapIndexed { index, leaf ->
            val directory = leaf.directory
            val psiDirectory = directory?.let { psiManager.findDirectory(it) }
            if (directory != null && psiDirectory != null) {
                MrWorkspaceTopFolderNode(myProject, psiDirectory, settings, leaf, pruningRoots, index)
            } else {
                // path 不存在或无法解析：仍显示顶层文件夹（文件夹图标叠警示角标），标题用 name 回退。
                MrWorkspaceMissingFolderNode(
                    myProject,
                    settings,
                    leaf.displayName,
                    leaf.locationText,
                    index,
                )
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
            absolutePathKey(resolved)
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
     * 顶层标题里跟在名称后方的路径文本（灰色小字）：以项目根（深度 0）为基准 —— 项目内显示相对路径；
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
        } catch (_: Exception) {
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

    /**
     * 多个文件且未在设置中指定时自动选用一个（解析失败会顺延），弹一次右下角气泡：
     * 可跳转设置页，也可点“不再提醒”直接把设置页里同名的复选框勾上（同一个布尔值，之后不再弹）。
     */
    private fun notifyIfAutoPicked(files: List<VirtualFile>, chosen: VirtualFile) {
        if (files.size <= 1) return
        val settings = getMrWorkspaceSettings(myProject)
        if (settings.state.selectedWorkspaceFile != null) return
        if (settings.state.neverNotifyMultipleWorkspaceFiles) return
        val key = files.joinToString("|") { it.name }
        if (autoPickNotifiedKey == key) return
        autoPickNotifiedKey = key

        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
        val notification = group.createNotification(
            NavifoxMessageBundle.message("MrWorkspacePane.notification.title"),
            NavifoxMessageBundle.message("MrWorkspacePane.notification.content", files.size, chosen.name),
            NotificationType.INFORMATION,
        )
        notification.addAction(
            NotificationAction.createSimple(
                NavifoxMessageBundle.message("MrWorkspacePane.notification.action.settings")
            ) {
                if (!myProject.isDisposed) {
                    openWorkspaceSettings(myProject)
                }
            })
        notification.addAction(
            NotificationAction.createSimple(
                NavifoxMessageBundle.message("MrWorkspacePane.notification.action.mute")
            ) {
                if (!myProject.isDisposed) {
                    getMrWorkspaceSettings(myProject).state.neverNotifyMultipleWorkspaceFiles = true
                }
                notification.expire()
            })
        Notifications.Bus.notify(notification, myProject)
    }

    // —— 自动刷新：原生重构/新建/删除/移动/重命名等 VFS 变化让目录树跟上磁盘 ——

    /**
     * 订阅 VFS 变化。只在组件首次构建（面板被实际显示）时执行一次，[dispose] 时断开。
     *
     * 平台只为“树内容已加载的节点”做 PSI 级子树刷新，而本视图用自定义节点 + 手动缓存
     * （[MrVisibleDirectoryNode]），原生动作后的变化经常到不了可见树；因此直接在 VFS 层
     * 感知结构变化，变化落在当前工作区根目录内时合并触发一次整树重载（等价于点工具栏刷新）。
     */
    private fun installAutoRefresh() {
        if (vfsBusConnection != null) return
        // 订阅随本面板一起释放（AbstractProjectViewPane 实现 Disposable），而不是把 Project 当父
        // 可处置对象：否则面板反复重建时每条连接都留到项目关闭，监听器会越积越多。
        val connection = myProject.messageBus.connect(this)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    for (event in events) {
                        when (event) {
                            is VFilePropertyChangeEvent ->
                                // 原生“重构 → 重命名”走 VFS 改名；只读/时间戳等属性变化不影响目录结构。
                                if (event.propertyName == VirtualFile.PROP_NAME) {
                                    onVfsStructureChange(event.file, event.file.parent)
                                }
                            is VFileContentChangeEvent ->
                                // 普通文件内容保存不影响目录结构；仅 *.code-workspace 内容决定 folders。
                                if (event.file.name.endsWith(WORKSPACE_SUFFIX)) {
                                    onVfsStructureChange(event.file, event.file.parent)
                                }
                            else -> {
                                // 新建/删除/移动/复制：以事件文件的路径是否落在工作区根内判断即可
                                // （路径前缀匹配不依赖父对象是否有效）；创建事件额外带父目录，
                                // 以便“直接在根目录内新建”也能命中。
                                val parent = (event as? VFileCreateEvent)?.parent
                                onVfsStructureChange(event.file, parent)
                            }
                        }
                    }
                }
            },
        )
        vfsBusConnection = connection
    }

    /**
     * VFS 结构变化入口：受影响路径不在本视图展示范围内则忽略，否则防抖调度一次刷新。
     *
     * @param file 事件主对象（删除后可能已失效，仅用其路径判断）。
     * @param parent 变化发生的父目录（删除/移动时更可靠，可为 null）。
     */
    private fun onVfsStructureChange(file: VirtualFile?, parent: VirtualFile?) {
        if (myProject.isDisposed) return
        if (!isAutoRefreshRelevant(file) && !isAutoRefreshRelevant(parent)) return
        scheduleAutoRefresh()
    }

    /** 判断路径变化是否会影响当前“多根工作区”视图的展示内容。 */
    private fun isAutoRefreshRelevant(file: VirtualFile?): Boolean {
        if (file == null) return false
        val path = file.path
        // ① 落在某个当前工作区根目录内（含根目录自身）：目录树内容随之变化。
        if (workspaceRootDirectories.any { root -> path == root.path || path.startsWith("${root.path}/") }) {
            return true
        }
        // ② 项目根目录第一层的 *.code-workspace：新增/删除/改名/保存都会影响“无可用配置”
        //    空态与（重新）加载结果。配置文件只出现在项目根第一层（见 core 的 findWorkspaceFiles）。
        if (path.endsWith(WORKSPACE_SUFFIX)) {
            val base = projectBaseDirPath ?: return false
            val slash = path.lastIndexOf('/')
            return slash > 0 && path.substring(0, slash) == base
        }
        return false
    }

    /** 合并调度一次自动刷新：连续事件不断重置计时器，停顿后才整树重载一次。 */
    private fun scheduleAutoRefresh() {
        if (myProject.isDisposed) return
        val schedule = Runnable {
            if (myProject.isDisposed) return@Runnable
            val timer = autoRefreshTimer
                ?: Timer(AUTO_REFRESH_DELAY_MS) {
                    if (!myProject.isDisposed) {
                        updateFromRoot(true)
                    }
                }.apply {
                    isRepeats = false
                    autoRefreshTimer = this
                }
            timer.restart()
        }
        if (SwingUtilities.isEventDispatchThread()) {
            schedule.run()
        } else {
            SwingUtilities.invokeLater(schedule)
        }
    }
}

/**
 * 让所有子组件铺满容器并重叠（先添加的在底层）：树常驻底层、空态覆盖层在其上，
 * 树因此始终处于显示/`isShowing` 状态，工具窗头部菜单等依赖目标组件可见性的平台行为不受影响。
 */
private object FillOverlayLayout : LayoutManager {
    override fun addLayoutComponent(name: String?, comp: Component) = Unit

    override fun removeLayoutComponent(comp: Component) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

    override fun layoutContainer(parent: Container) {
        for (i in 0 until parent.componentCount) {
            parent.getComponent(i).setBounds(0, 0, parent.width, parent.height)
        }
    }
}

/**
 * 把 [File] 转成**绝对规范化路径键**：正斜杠、已解析 `.`／`..`、绝对路径。
 *
 * 之所以不能直接用 [java.io.File.getPath]：Windows 上它是**反斜杠**，而 [VirtualFile.getPath] 一律是
 * **正斜杠**，两者直接比较永远不相等（历史缺陷：内容根去重曾因此在 Windows 上完全失效）。
 * 凡是拿路径当“目录身份”比较的地方（去重、剪枝、与项目根比对）都必须先过这个函数。
 */
internal fun absolutePathKey(file: File): String =
    file.toPath().normalize().toAbsolutePath().toString().replace('\\', '/')

/** 供设置页在 apply 后触发当前项目里所有已创建面板刷新。 */
internal object MrWorkspacePanes {
    private val panes = CopyOnWriteArrayList<MrWorkspacePane>()

    fun register(pane: MrWorkspacePane) {
        if (!panes.contains(pane)) {
            panes.add(pane)
        }
    }

    fun unregister(pane: MrWorkspacePane) {
        panes.remove(pane)
    }

    /**
     * 整树重载当前项目的所有面板。
     *
     * @param expandFolderKey 不为 null 时，在面板重载完成后按该选择键（[folderSelectionKeyOf]）
     *   把对应顶层文件夹的虚拟文件交给平台定位：平台会展开沿途节点并滚动到那一行，
     *   用户因此直接看到所选文件夹里的内容（“文件夹”菜单选中单项后的效果）。
     */
    fun refresh(project: Project, expandFolderKey: String? = null) {
        panes.filter { it belongsTo project }.forEach { pane ->
            val callback = pane.updateFromRoot(true)
            if (expandFolderKey != null) {
                callback.doWhenDone { pane.expandTopFolder(expandFolderKey) }
            }
        }
    }
}

/** 自动刷新防抖窗口（毫秒）：期间内 VFS 事件合并为一次整树重载。 */
private const val AUTO_REFRESH_DELAY_MS = 350

/** 同一批文件只弹一次自动选择提醒，避免刷新时反复打扰。 */
@Volatile
private var autoPickNotifiedKey: String? = null

/** 全部候选 *.code-workspace 均不可用时的诊断日志。 */
private val LOG = Logger.getInstance(MrWorkspacePane::class.java)
