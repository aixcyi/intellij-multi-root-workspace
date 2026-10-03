package net.navifox.plugins.workspace

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThreadAware
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.impl.ExpandableComboAction
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.MrWorkspaceUnusableException
import net.navifox.plugins.core.findWorkspaceFiles

/**
 * New UI 主工具栏左侧的小组件（紧跟在 Project Widget 之后）：一眼看到当前只显示哪个顶层文件夹，
 * 点开就是与工具窗口头部“文件夹”子菜单完全相同的列表，选中后**自动激活工具窗口并过滤**到该文件夹。
 *
 * 为什么用 [ExpandableComboAction]：Project Widget（平台的
 * `com.intellij.openapi.wm.impl.headertoolbar.ProjectToolbarWidgetAction`）就是它的子类，
 * 外观（图标 ＋ 文本 ＋ 下拉箭头、悬停/选中背景）与定位行为因此与 Project Widget 一致；
 * 只需要实现 [createPopup] 返回待弹出的菜单，平台会把菜单显示在小组件正下方。
 *
 * 面板还没被打开过也能用：菜单数据不读面板上一次渲染的产物，而是按项目现算
 * （[MrWorkspaceFoldersMenuData.forProject]），与面板将要渲染的内容同源。
 */
internal class MrWorkspaceFoldersWidgetAction : ExpandableComboAction(), DumbAware, ActionUpdateThreadAware {

    init {
        // 动作自身的文案（与面板标题一致）：显示不出来时也会出现在“自定义工具栏”等平台界面里，
        // 不能为空，否则平台会报 “Empty menu item text”。
        templatePresentation.text = NavifoxMessageBundle.message("MrWorkspacePane.folders")
        templatePresentation.description =
            NavifoxMessageBundle.message("MrWorkspacePane.foldersWidget.description")
        templatePresentation.icon = AllIcons.Nodes.Workspace
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * 有可用配置时显示，文案＝当前选中的顶层文件夹名（“显示所有”时为“所有文件夹”）。
     *
     * 项目里一个 `*.code-workspace` 都没有、或用户在设置里勾了“隐藏工具栏上的‘文件夹’小组件”时，
     * 整个小组件都不出现。这里只看“有没有配置文件”（列目录，不读文件内容），负担足够低。
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDisposed) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = !getMrWorkspaceSettings(project).state.hideFoldersWidget &&
            findWorkspaceFiles(project).isNotEmpty()
        e.presentation.isEnabled = true
        e.presentation.icon = AllIcons.Nodes.Workspace
        e.presentation.setText(
            getMrWorkspaceFolderFilter(project).selectedFolderName
                ?: NavifoxMessageBundle.message("MrWorkspacePane.foldersWidget.all"),
            false,
        )
        e.presentation.description =
            NavifoxMessageBundle.message("MrWorkspacePane.foldersWidget.description")
    }

    /**
     * 点击（或键盘触发）时构建“文件夹”菜单；平台负责把它显示在小部件正下方。
     *
     * 返回 null 表示没有可弹出的内容（没有配置文件，或全部配置文件都不可用）。
     */
    override fun createPopup(event: AnActionEvent): JBPopup? {
        val project = event.project ?: return null
        if (project.isDisposed) return null
        val data = try {
            MrWorkspaceFoldersMenuData.forProject(project)
        } catch (ex: MrWorkspaceUnusableException) {
            // 配置文件都不可用时没有可选的文件夹：面板空态里会给出解释与“创建配置文件”入口。
            LOG.warn("No usable *.code-workspace file: ${ex.message}")
            null
        } ?: return null
        return createFoldersPopup(data, event.dataContext, activateToolWindow = true)
    }
}

/** 全部候选 `*.code-workspace` 均不可用时的诊断日志。 */
private val LOG = Logger.getInstance(MrWorkspaceFoldersWidgetAction::class.java)
