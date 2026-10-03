package net.navifox.plugins.workspace

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import net.navifox.plugins.NavifoxMessageBundle

/**
 * “文件夹”菜单的数据：把当前**实际渲染**的顶层文件夹整理成可选项。
 *
 * 菜单内容与面板共用同一份真相（[MrWorkspaceFolderModel]，即面板的加载、内容根去重与隐藏设置），
 * 不会出现“菜单里有、树里却没有”的错位。
 *
 * @param project 菜单所属项目：菜单项的触发不依赖弹窗的数据上下文，因此在这里显式携带。
 * @param leaves 可选择的文件夹（顺序与面板一致）；路径无法解析的文件夹不在这里。
 * @param visibleKeys 当前实际显示的文件夹选择键，用于判断“显示所有”是否已经是当前状态。
 */
internal class MrWorkspaceFoldersMenuData(
    val project: Project,
    val leaves: List<MrFolderLeaf>,
    val visibleKeys: Set<String>,
) {
    companion object {
        /**
         * “显示所有”项的选择键。
         *
         * 路径键必然是绝对路径（含盘符或根斜杠），不可能是空串，因此不会与任何真实文件夹冲突。
         */
        const val ALL_FOLDERS_KEY = ""

        /**
         * 从已构建好树的面板里取出菜单数据（工具窗口头部的子菜单与 Alt＋F2 用）；
         * 面板当前没有可展示的文件夹时返回 null。
         */
        fun from(pane: MrWorkspacePane): MrWorkspaceFoldersMenuData? {
            val leaves = pane.getFolderMenuLeaves()
            if (leaves.isEmpty()) return null
            return MrWorkspaceFoldersMenuData(pane.project(), leaves, pane.getVisibleFolderKeys())
        }

        /**
         * 直接按项目计算菜单数据，**不要求面板已建好树**（主工具栏小组件用：面板可能还没被打开过）。
         *
         * 与面板共用 [loadFolderSnapshot]，因此这里列出的文件夹与面板将要渲染的完全一致——
         * 与面板路径的区别只是“读的是最新计算结果”而非“读面板上一次渲染的产物”。
         *
         * @return null 表示项目根目录没有任何 `*.code-workspace` 文件。
         * @throws net.navifox.plugins.core.MrWorkspaceUnusableException 全部候选文件都不可用。
         */
        fun forProject(project: Project): MrWorkspaceFoldersMenuData? {
            val snapshot = loadFolderSnapshot(project) ?: return null
            // 路径解析不到的 folder 留在树里（警示节点），但没有可比较的目录身份，因此不进菜单。
            val leaves = snapshot.leaves.filter { it.resolved != null }
            return MrWorkspaceFoldersMenuData(project, leaves, snapshot.leaves.map { it.key }.toSet())
        }
    }
}

/**
 * “文件夹”菜单：整体是一个**动作组**（[DefaultActionGroup]），因此
 *
 * - 放进工具窗口头部的下拉菜单时是**子菜单**，就地展开，不另弹独立菜单；
 * - [actionPerformed]（以及快捷键入口 [MrFoldersMenuAction]）用平台标准的
 *   `createActionGroupPopup` 弹出同一份列表，与 Alt＋F1“在…中选中”**同一套机制**。
 *
 * 编号交给平台的 `ActionSelectionAid.ALPHA_NUMBERING`（`NumericMnemonicItem` 那套数字助记）：
 * 选项文本里**不带**编号，平台按 `1`~`9`、`0`、`A`… 自动分配助记键。
 */
internal class MrWorkspaceFoldersMenuAction : DefaultActionGroup(
    NavifoxMessageBundle.message("MrWorkspacePane.folders"),
    true,
), DumbAware, ActionUpdateThreadAware {

    init {
        // 与工具窗口下拉框里的面板图标一致（文件夹 ＋ 修饰角标），便于一眼认出这是“多根工作区”的菜单。
        templatePresentation.icon = MrWorkspaceIcons.plugin
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 每次更新都按当前面板状态重建子项（子菜单因此永远反映最新树）。
     *
     * 面板不可用（例如正停在原生“项目”面板）时清空并禁用自己，头部菜单里就不会出现一个点不出东西的子菜单。
     */
    override fun update(e: AnActionEvent) {
        val data = paneOf(e)?.let { MrWorkspaceFoldersMenuData.from(it) }
        if (data == null) {
            removeAll()
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = true
        removeAll()
        addAll(folderMenuActions(data))
    }

    /** 动作组被直接触发时（例如放进工具栏按钮）弹出与子菜单相同的列表。 */
    override fun actionPerformed(e: AnActionEvent) {
        showFoldersMenuPopup(e)
    }
}

/**
 * 快捷键入口（`Alt＋F2`，ID 见 `plugin.xml` 的 `MrWorkspace.ShowFoldersMenu`）。
 *
 * 平台不允许把 `ActionGroup` 注册成 `<action>`（会报“ActionGroup should be registered using &lt;group&gt; tag”），
 * 因此快捷键用这个具名动作，弹出的内容与头部子菜单那份 [folderMenuActions] 完全一致。
 */
internal class MrFoldersMenuAction : DumbAwareAction(
    NavifoxMessageBundle.message("MrWorkspacePane.folders"),
    NavifoxMessageBundle.message("MrWorkspacePane.folders.description"),
    MrWorkspaceIcons.plugin,
), ActionUpdateThreadAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = paneOf(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        showFoldersMenuPopup(e)
    }
}

/** 用平台标准的动作组弹窗弹出“文件夹”列表；`Alt＋F2` 与头部子菜单共用。 */
private fun showFoldersMenuPopup(e: AnActionEvent) {
    val pane = paneOf(e) ?: return
    val data = MrWorkspaceFoldersMenuData.from(pane) ?: return
    createFoldersPopup(data, e.dataContext, activateToolWindow = false).showInBestPositionFor(e.dataContext)
}

/**
 * 构建“文件夹”列表弹窗（尚未显示）：由 `Alt＋F2` 弹窗、[MrWorkspaceFoldersWidgetAction] 的小组件菜单共用。
 *
 * @param activateToolWindow 选中后是否先把“项目”工具窗口切到本面板（小组件在别的视图下也能用，
 *   因此它需要；工具窗口头部子菜单与 Alt＋F2 本来就在本面板里，不需要额外激活）。
 */
internal fun createFoldersPopup(
    data: MrWorkspaceFoldersMenuData,
    dataContext: DataContext,
    activateToolWindow: Boolean,
): JBPopup =
    JBPopupFactory.getInstance()
        .createActionGroupPopup(
            NavifoxMessageBundle.message("MrWorkspacePane.folders"),
            DefaultActionGroup().apply { addAll(folderMenuActions(data, activateToolWindow)) },
            dataContext,
            // ALPHA_NUMBERING：与 Alt＋F1 同一套编号（1~9、0、A…），助记符由平台分配，项文本里不带编号。
            JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING,
            false, // 不显示被禁用的项（这里的项永远可用）。
            null,
            -1,
            null,
            null,
        )

/**
 * 列表内容：“显示所有文件夹” ＋ 分隔线 ＋ 各顶层文件夹。
 *
 * 编号不写进文本（由平台的 `ALPHA_NUMBERING` 统一处理），因此这里的文本是干净的文件夹名。
 * 工具窗口头部的子菜单、`Alt＋F2` 弹窗与主工具栏小组件的菜单都走这里，内容永远一致。
 */
private fun folderMenuActions(
    data: MrWorkspaceFoldersMenuData,
    activateToolWindow: Boolean = false,
): List<AnAction> = buildList {
    add(SelectionAction(
        project = data.project,
        key = MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY,
        label = NavifoxMessageBundle.message("MrWorkspacePane.folders.showAll"),
        activateToolWindow = activateToolWindow,
    ))
    // 一个文件夹都没有时（工作区没声明 folders，或全被“自动隐藏当前工作区目录”隐藏）不画分隔线。
    if (data.leaves.isEmpty()) return@buildList
    add(Separator())
    data.leaves.forEach { leaf ->
        add(SelectionAction(
            project = data.project,
            key = leaf.key,
            label = leaf.displayName,
            activateToolWindow = activateToolWindow,
        ))
    }
}

/**
 * 一个可选中的条目：把“选择键”与展示文本绑起来，点中后应用过滤。
 *
 * 项目由菜单构建时捕获（而不是从事件的 [DataContext] 里取）：主工具栏弹窗的数据上下文不保证带 Project。
 */
private class SelectionAction(
    private val project: Project,
    private val key: String,
    private val label: String,
    private val activateToolWindow: Boolean,
) : DumbAwareAction(label), ActionUpdateThreadAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 给“当前正在显示”的那一项打勾：过滤中时是被选中的那个文件夹，未过滤时是“显示所有文件夹”。
     *
     * 用**图标**而非 `ToggleAction`：`Alt＋F2` 与小组件走 `JBPopupFactory.createActionGroupPopup`，
     * 那条路径（`ActionPopupStep`／`PopupFactoryImpl`）不识别 `Toggleable`，原生复选框不会出现
     * （已 javap 核实：只有菜单路径的 `ActionMenuItem` 处理它）；而图标槽两条路径都会渲染。
     */
    override fun update(e: AnActionEvent) {
        val selected = getMrWorkspaceFolderFilter(project).selectedFolderKey
        val checked = if (key == MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY) selected == null else selected == key
        e.presentation.icon = if (checked) AllIcons.Actions.Checked else null
        e.presentation.selectedIcon = if (checked) AllIcons.Actions.Checked else null
    }

    override fun actionPerformed(e: AnActionEvent) {
        applySelection(
            project = project,
            key = key,
            displayName = label.takeIf { key != MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY },
            activateToolWindow = activateToolWindow,
        )
    }
}

/**
 * 应用一次选择：命中文件夹则只显示它（并把该顶层目录展开到可见），否则（含“显示所有”）显示全部顶层文件夹。
 *
 * 过滤状态只存在内存里（见 [MrWorkspaceFolderFilter]）：面板切走再切回、乃至重启 IDE 都会回到“显示所有”。
 *
 * @param displayName 命中文件夹时记下的显示名，供主工具栏小组件显示“当前在看哪个文件夹”。
 * @param activateToolWindow 是否先把“项目”工具窗口切到本面板（主工具栏小组件需要，见 [createFoldersPopup]）。
 */
internal fun applySelection(
    project: Project,
    key: String,
    displayName: String? = null,
    activateToolWindow: Boolean = false,
) {
    if (project.isDisposed) return
    val filter = getMrWorkspaceFolderFilter(project)
    if (key == MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY) {
        filter.clear()
    } else {
        filter.setSelection(key, displayName)
    }
    if (activateToolWindow) {
        activateWorkspacePane(project)
    }
    // 重新加载完成后按路径定位一次：平台会展开沿途节点并滚动到该行，用户直接看到这个文件夹里的内容。
    MrWorkspacePanes.refresh(project, key.takeIf { it != MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY })
}

/**
 * 把“项目”工具窗口显示出来并切到本插件面板。
 *
 * 先 `activate` 再 `changeView`：平台要等工具窗口的内容（各面板的 Content）建好之后才能切面板，
 * 而内容是在工具窗口第一次显示时创建的。
 */
private fun activateWorkspacePane(project: Project) {
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.activate(null, true)
    ProjectView.getInstance(project).changeView(MrWorkspacePane.ID)
}

/** 项目当前显示的就是本插件面板时返回该面板；否则返回 null（此时子菜单不出现、快捷键不响应）。 */
private fun paneOf(e: AnActionEvent): MrWorkspacePane? {
    val project = e.project ?: return null
    if (project.isDisposed) return null
    val current = ProjectView.getInstance(project).currentProjectViewPane ?: return null
    return if (current.id == MrWorkspacePane.ID) current as? MrWorkspacePane else null
}

/**
 * “文件夹”菜单的过滤状态：当前只看哪一个顶层文件夹。
 *
 * 作用域是**项目**：同一项目里面板无论重建多少次都共用同一份状态。**不持久化**——
 * 重启 IDE、重新打开项目都会回到“显示所有”，不会留下失效的残留状态。
 */
@Service(Service.Level.PROJECT)
internal class MrWorkspaceFolderFilter {

    /**
     * 当前选择（选择键 ＋ 显示名），null 表示“显示所有”。
     *
     * 键与显示名放在同一个不可变对象里、整体一次性替换：[MrWorkspacePane] 的树在后台线程读它，
     * 主工具栏小组件在 EDT 读它，分开两个字段会出现“读到新键、旧名”的错位。
     */
    @Volatile
    private var current: MrFolderSelection? = null

    /** 当前选中文件夹的选择键（[folderSelectionKey] 生成）；null 表示“显示所有”。 */
    val selectedFolderKey: String? get() = current?.key

    /** 当前选中文件夹的显示名（主工具栏小组件的文案）；null 表示“显示所有”。 */
    val selectedFolderName: String? get() = current?.displayName

    /** 最近一次渲染时**实际显示**的顶层文件夹选择键；面板尚未建好树时为空集。 */
    @Volatile
    var visibleFolderKeys: Set<String> = emptySet()
        internal set

    internal fun setSelection(key: String, displayName: String?) {
        current = MrFolderSelection(key, displayName)
    }

    internal fun clear() {
        current = null
    }

    internal fun setVisibleKeys(keys: Set<String>) {
        visibleFolderKeys = keys
    }
}

/** 一次“只看某个顶层文件夹”的选择；不可变，整体替换以避免读到半新半旧的状态。 */
internal class MrFolderSelection(val key: String, val displayName: String?)

/** 项目级过滤状态通过 Project.getService 获取（ServiceManager 已在 Java 层废弃）。 */
internal fun getMrWorkspaceFolderFilter(project: Project): MrWorkspaceFolderFilter =
    project.getService(MrWorkspaceFolderFilter::class.java)
