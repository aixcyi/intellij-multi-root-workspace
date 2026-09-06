package net.navifox.plugins.workspace

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import net.navifox.plugins.NavifoxMessageBundle

/**
 * “在此视图中选择”（SelectIn，Alt＋F1）时把目标文件或目录定位到本面板树里的目标实现。
 *
 * - 注册到扩展点 `com.intellij.selectInTarget`，因此会出现在全局“在…中选中”列表；
 * - [MrWorkspaceViewPane.createSelectInTarget] 返回同一实现，供项目视图内部按“当前面板”定位，
 *   两条路径行为一致。
 *
 * 选中前会把 Project 工具窗口切到本面板（changeView），再交给平台（ProjectView.select → 面板
 * select）完成展开与选中；目标是否真的在树中由平台遍历按需加载后决定。树经过“内容根去重”后，
 * 同一文件只有唯一节点，因此无需处理重复命中的情况。
 */
class MrWorkspaceSelectInTarget(private val project: Project) : SelectInTarget, DumbAware {

    // 在菜单里显示的代表自己的文本，所以直接用面板标题就好了。
    override fun toString(): String = NavifoxMessageBundle.message("MrWorkspaceViewPane.title")

    // 用 Project View 的 ID 表示挂靠到这个下面。
    override fun getToolWindowId(): String = ToolWindowId.PROJECT_VIEW

    // 子菜单对应的 ID：必须是面板自身的 ID（平台要求 minorViewId 与 pane 的 id 一致）。
    override fun getMinorViewId(): String = MrWorkspaceViewPane.ID

    // 与内置“项目”目标相同的权重，让本项与“项目”相邻展示。
    override fun getWeight(): Float = 1f

    private fun findPane(): MrWorkspaceViewPane? =
        ProjectView.getInstance(project).getProjectViewPaneById(MrWorkspaceViewPane.ID) as? MrWorkspaceViewPane

    private fun currentRoots(): List<VirtualFile> = findPane()?.workspaceRootDirectories ?: emptyList()

    /** 判断 [file] 是否就是 [root] 或位于其下（用于限制“在此视图中选择”的范围）。 */
    private fun isSameOrUnder(root: VirtualFile, file: VirtualFile): Boolean =
        root === file || VfsUtilCore.isAncestor(root, file, true)

    override fun canSelect(context: SelectInContext): Boolean {
        if (project.isDisposed) return false
        val file = context.virtualFile
        if (!file.isValid) return false
        // 根目录缓存为空说明面板尚未完成首次加载：此时不武断判定“不可选”，放行交给平台遍历，
        // 由树加载结果决定文件是否真的在其中（加载由 select 的遍历按需触发）。
        val roots = currentRoots()
        return roots.isEmpty() || roots.any { root -> isSameOrUnder(root, file) }
    }

    override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
        if (project.isDisposed) return
        val file = context.virtualFile
        val roots = currentRoots()
        // 仅当缓存明确且文件不在任何工作区根目录下时才剪枝；缓存为空时交给平台遍历判定，
        // 避免“面板尚未加载就把合法目标误杀”。
        if (roots.isNotEmpty() && roots.none { root -> isSameOrUnder(root, file) }) {
            return
        }
        val view = ProjectView.getInstance(project)
        // 手动触发会先保证工具窗口初始化并加载本面板；自动滚动场景由平台自行决定窗口状态。
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.let { window ->
            if (requestFocus) {
                window.activate(null, true)
            }
        }
        if (findPane() == null) {
            return
        }
        view.changeView(MrWorkspaceViewPane.ID)
        // 以文件本身作为定位对象：本面板展示的是文件级节点（不含成员），直接走纯文件定位，
        // 避免按 PSI 元素匹配时因树中不存在对应元素节点而失败。
        view.select(file, file, requestFocus)
    }
}
