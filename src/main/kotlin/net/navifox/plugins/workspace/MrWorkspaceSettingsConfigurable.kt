package net.navifox.plugins.workspace

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.WORKSPACE_SUFFIX
import net.navifox.plugins.core.findWorkspaceFiles
import net.navifox.plugins.core.resolveWorkspaceFile
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

/**
 * Settings → Tools 下的设置页：指定 Project 面板读取哪个 `*.code-workspace` 文件。
 *
 * - 页面只有“文本标签 + 下拉框”，无多余说明文字；
 * - 没有可用配置（项目根目录下无任何 `*.code-workspace` 文件）时下拉框不置灰，
 *   自动选中一个“（无可用配置文件）”占位选项——该选项不会被保存（Apply 时写 `null`）；
 * - 新建配置的入口在“多根工作区”工具窗口的空态里（[MrWorkspaceViewPane] 中的创建链接）；
 * - 布局用平台 UI DSL，选中值与设置状态的读写沿用 `SearchableConfigurable` 生命周期
 *   （`null` 表示自动检测，无法用简单的绑定表达，故不注册 DSL 回调）。
 */
class MrWorkspaceSettingsConfigurable(private val project: Project) : SearchableConfigurable {

    companion object {
        const val ID = "net.navifox.plugins.workspace.settings"
    }

    private var fileCombo: ComboBox<String>? = null

    /** 复选框组件；由本类自行做状态比对（不使用 DSL 绑定，见 [isModified]）。 */
    private var hideWorkspaceDirCheckBox: JCheckBox? = null

    /** 复选框创建时对应的已保存状态，用于判断当前是否被改动。 */
    private var hideWorkspaceDirResetValue: Boolean = false

    override fun getId(): String = ID

    override fun getDisplayName(): String =
        NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.title")

    override fun createComponent(): JComponent {
        val combo = ComboBox<String>()
        combo.isEditable = false
        rebindCombo(combo)
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val fullName = value as? String
                val comp = super.getListCellRendererComponent(
                    list, fullName?.removeSuffix(WORKSPACE_SUFFIX) ?: "", index, isSelected, cellHasFocus,
                ) as JLabel
                // 占位选项不是真实文件，不需要完整文件名提示。Swing 会把以 `<html>` 开头的工具提示
                // 当 HTML 解析，所以先转义再交给 setToolTipText（String 重载在 253 与 261 的发行版 jar
                // 里都存在，而 setToolTipText(HtmlChunk) 重载在本项目可用的平台上并不存在）。
                comp.toolTipText = fullName
                    ?.takeIf { it.endsWith(WORKSPACE_SUFFIX) }
                    ?.let(StringUtil::escapeXmlEntities)
                return comp
            }
        }
        fileCombo = combo

        return panel {
            row(NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.sourceLabel")) {
                cell(combo)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }
            group(NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.group.display")) {
                row {
                    checkBox(NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.hideWorkspaceDirectory"))
                        .also { hideWorkspaceDirCheckBox = it.component }
                }.layout(RowLayout.PARENT_GRID)
            }
        }
    }

    override fun isModified(): Boolean {
        // 两个控件都自行比对：下拉框的选择值语义（`null` 表示自动检测）无法用绑定表达，
        // 复选框若用绑定则由平台比对，会把“创建时已是勾选”的情况误判成已修改。
        if ((hideWorkspaceDirCheckBox?.isSelected ?: hideWorkspaceDirResetValue) != hideWorkspaceDirResetValue) {
            return true
        }
        val files = findWorkspaceFiles(project)
        if (files.isEmpty()) return false // 只有占位选项，无可保存的配置源修改
        val selected = fileCombo?.selectedItem as? String
        val stored = getMrWorkspaceSettings(project).state.selectedWorkspaceFile
        val effectiveWhenAuto = files.firstOrNull()?.name
        return if (stored == null) {
            selected != effectiveWhenAuto
        } else {
            selected != stored
        }
    }

    override fun apply() {
        // 占位选项或空选择不落盘（null = 自动检测）
        val selected = (fileCombo?.selectedItem as? String)?.takeIf { it.endsWith(WORKSPACE_SUFFIX) }
        val settings = getMrWorkspaceSettings(project)
        settings.state.selectedWorkspaceFile = selected
        settings.state.hideWorkspaceDirectory = hideWorkspaceDirCheckBox?.isSelected ?: false
        MrWorkspacePanes.refresh(project)
    }

    override fun reset() {
        fileCombo?.let { rebindCombo(it) }
        val stored = getMrWorkspaceSettings(project).state.hideWorkspaceDirectory
        hideWorkspaceDirResetValue = stored
        hideWorkspaceDirCheckBox?.isSelected = stored
    }

    /**
     * 用当前扫描结果重建下拉项：没有可用配置时只放占位选项（不持久化、不置灰），
     * 否则放全部文件名并预选（沿用设置值；设置值为空时自动选中按文件名序第一个）。
     */
    private fun rebindCombo(combo: ComboBox<String>) {
        val files = findWorkspaceFiles(project)
        combo.removeAllItems()
        if (files.isEmpty()) {
            combo.addItem(NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.noConfigOption"))
            combo.selectedItem = combo.getItemAt(0)
        } else {
            files.forEach { combo.addItem(it.name) }
            val stored = getMrWorkspaceSettings(project).state.selectedWorkspaceFile
            combo.selectedItem = presetName(files, stored)
        }
    }

    /** combo 的“重置值”：设置了且文件仍存在 → 设置值；否则回落到自动选中的那个。 */
    private fun presetName(files: List<VirtualFile>, stored: String?): String? {
        if (files.isEmpty()) return null
        if (stored != null && files.any { it.name == stored }) return stored
        return resolveWorkspaceFile(files, stored)?.name
    }
}

/** 供气泡动作打开本设置页（伴生对象只允许常量，故置于顶层）。 */
internal fun openWorkspaceSettings(project: Project) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, MrWorkspaceSettingsConfigurable::class.java)
}
