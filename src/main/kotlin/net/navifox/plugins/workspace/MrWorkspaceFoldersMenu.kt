package net.navifox.plugins.workspace

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import net.navifox.plugins.NavifoxMessageBundle
import java.io.File

/**
 * “文件夹”菜单的数据：把面板当前**实际渲染**的顶层文件夹整理成可选项。
 *
 * 菜单内容与面板共用同一份真相（[MrWorkspacePane] 的加载、内容根去重与隐藏设置），
 * 不会出现“菜单里有、树里却没有”的错位。
 *
 * @param leaves 可选择的文件夹（顺序与面板一致）；路径无法解析的文件夹不在这里。
 * @param visibleKeys 当前实际显示的文件夹选择键，用于判断“显示所有”是否已经是当前状态。
 */
internal class MrWorkspaceFoldersMenuData(
    val leaves: List<MrWorkspacePane.MrFolderLeaf>,
    val visibleKeys: Set<String>,
) {
    companion object {
        /**
         * “显示所有”项的选择键。
         *
         * 路径键必然是绝对路径（含盘符或根斜杠），不可能是空串，因此不会与任何真实文件夹冲突。
         */
        const val ALL_FOLDERS_KEY = ""

        /** 从已构建好树的面板里取出菜单数据；面板当前没有可展示的文件夹时返回 null。 */
        fun from(pane: MrWorkspacePane): MrWorkspaceFoldersMenuData? {
            val leaves = pane.getFolderMenuLeaves()
            if (leaves.isEmpty()) return null
            return MrWorkspaceFoldersMenuData(leaves, pane.getVisibleFolderKeys())
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
        // 与工具窗口下拉框里的面板图标一致（多文件夹），便于一眼认出这是“多根工作区”的菜单。
        templatePresentation.icon = AllIcons.Nodes.Workspace
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
    AllIcons.Nodes.Workspace,
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
    JBPopupFactory.getInstance()
        .createActionGroupPopup(
            NavifoxMessageBundle.message("MrWorkspacePane.folders"),
            DefaultActionGroup().apply { addAll(folderMenuActions(data)) },
            e.dataContext,
            // ALPHA_NUMBERING：与 Alt＋F1 同一套编号（1~9、0、A…），助记符由平台分配，项文本里不带编号。
            JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING,
            false, // 不显示被禁用的项（这里的项永远可用）。
            null,
            -1,
            null,
            null,
        )
        .showInBestPositionFor(e.dataContext)
}

/**
 * 列表内容：“显示所有文件夹” ＋ 分隔线 ＋ 各顶层文件夹。
 *
 * 编号不写进文本（由平台的 `ALPHA_NUMBERING` 统一处理），因此这里的文本是干净的文件夹名。
 * 工具窗口头部的子菜单与 `Alt＋F2` 弹窗都走这里，两者内容永远一致。
 */
private fun folderMenuActions(data: MrWorkspaceFoldersMenuData): List<AnAction> = buildList {
    add(SelectionAction(MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY,
                        NavifoxMessageBundle.message("MrWorkspacePane.folders.showAll")))
    add(Separator())
    data.leaves.forEach { leaf -> add(SelectionAction(leaf.key, leaf.displayName)) }
}

/** 一个可选中的条目：把“选择键”与展示文本绑起来，点中后应用过滤。 */
private class SelectionAction(
    private val key: String,
    label: String,
) : DumbAwareAction(label), ActionUpdateThreadAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        applySelection(project, key)
    }
}

/**
 * 应用一次选择：命中文件夹则只显示它（并把该顶层目录展开到可见），否则（含“显示所有”）显示全部顶层文件夹。
 *
 * 过滤状态只存在内存里（见 [MrWorkspaceFolderFilter]）：面板切走再切回、乃至重启 IDE 都会回到“显示所有”。
 */
internal fun applySelection(project: Project, key: String) {
    if (project.isDisposed) return
    val filter = getMrWorkspaceFolderFilter(project)
    if (key == MrWorkspaceFoldersMenuData.ALL_FOLDERS_KEY) {
        filter.clear()
        MrWorkspacePanes.refresh(project)
        return
    }
    filter.setSelection(key)
    // 重新加载完成后按路径定位一次：平台会展开沿途节点并滚动到该行，用户直接看到这个文件夹里的内容。
    MrWorkspacePanes.refresh(project, key)
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

    /** 当前选中文件夹的选择键（[folderSelectionKey] 生成）；null 表示“显示所有”。 */
    @Volatile
    var selectedFolderKey: String? = null
        private set

    /** 最近一次渲染时**实际显示**的顶层文件夹选择键；面板尚未建好树时为空集。 */
    @Volatile
    var visibleFolderKeys: Set<String> = emptySet()
        internal set

    internal fun setSelection(key: String) {
        selectedFolderKey = key
    }

    internal fun clear() {
        selectedFolderKey = null
    }

    internal fun setVisibleKeys(keys: Set<String>) {
        visibleFolderKeys = keys
    }
}

/** 项目级过滤状态通过 Project.getService 获取（ServiceManager 已在 Java 层废弃）。 */
internal fun getMrWorkspaceFolderFilter(project: Project): MrWorkspaceFolderFilter =
    project.getService(MrWorkspaceFolderFilter::class.java)

/**
 * “文件夹”菜单里一个文件夹的**选择键**：正斜杠的绝对规范化路径。
 *
 * [VirtualFile.getPath] 本身就是正斜杠绝对路径；这里再走一遍 [absolutePathKey]，是为了和
 * [MrWorkspacePane] 里其它按路径比较的地方（内容根去重、与项目根比对）保持同一套写法。
 */
internal fun folderSelectionKey(directory: VirtualFile): String = absolutePathKey(File(directory.path))

/** 与 [folderSelectionKey] 同理，只是输入来自已解析的 [File]（内容根去重与剪枝用的也是这个键）。 */
internal fun folderSelectionKeyOf(file: File): String = absolutePathKey(file)
