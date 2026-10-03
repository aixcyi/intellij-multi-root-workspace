package net.navifox.plugins.workspace

import com.intellij.ui.IconManager
import javax.swing.Icon

/**
 * 本插件的图标：搬进插件、并把“折线”改成 sourceRoot 文件夹边框色的 workspace 图标。
 *
 * - 资源：`resources/icons/workspace[_dark].svg`（面板／菜单）与 `resources/icons/workspaceWidget[_dark].svg`
 *   （主工具栏小组件，文件夹内面纯透明）；深色模式由平台按同名 `_dark` 约定自动切换。
 * - 折线颜色：浅色 `#3574F0`、深色 `#548AF7`，取自平台 `expui/nodes/sourceRoot[_dark].svg`
 *   里文件夹的边框色。
 * - 市场与“插件”列表里的图标是 `META-INF/pluginIcon[_dark].svg`（同一画法，仅把画布声明为 128）。
 */
internal object MrWorkspaceIcons {

    /** 插件图标：面板图标（“项目”工具窗口的面板下拉框）、树根节点、“文件夹”菜单及其快捷键动作。 */
    val plugin: Icon by lazy { load("/icons/workspace.svg") }

    /** 主工具栏“文件夹”小组件的图标：同画法，但文件夹内面纯透明。 */
    val widget: Icon by lazy { load("/icons/workspaceWidget.svg") }

    /** 用插件自己的类加载器取图标（`getIcon(path, Class)` 已废弃）。 */
    private fun load(path: String): Icon =
        IconManager.getInstance().getIcon(path, MrWorkspaceIcons::class.java.classLoader)
}
