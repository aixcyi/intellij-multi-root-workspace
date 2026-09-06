package net.navifox.plugins.workspace

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.WORKSPACE_SUFFIX
import net.navifox.plugins.core.findWorkspaceFiles
import net.navifox.plugins.core.resolveWorkspaceFile
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

/**
 * Settings → Tools 下的设置页：指定 Project 面板读取哪个 `*.code-workspace` 文件。
 * 下拉框中只显示去掉 `.code-workspace` 后缀的名称，并默认定位到当前正在使用的文件。
 */
class MrWorkspaceSettingsConfigurable(private val project: Project) : SearchableConfigurable {

    companion object {
        const val ID = "net.navifox.plugins.workspace.settings"
    }

    private fun displayNameText(): String =
        NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.title")

    private fun sourceLabel(): String =
        NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.sourceLabel")

    private var fileCombo: ComboBox<String>? = null

    override fun getId(): String = ID

    override fun getDisplayName(): String = displayNameText()

    override fun createComponent(): JComponent {
        val files = findWorkspaceFiles(project)
        val stored = getMrWorkspaceSettings(project).state.selectedWorkspaceFile
        val effective = resolveWorkspaceFile(files, stored)

        val combo = ComboBox<String>()
        combo.isEditable = false
        files.forEach { combo.addItem(it.name) }
        if (files.isNotEmpty()) {
            // 默认定位到正在使用的文件：手动指定过则用指定值，否则是自动选中的那个
            val preselect = if (stored != null && files.any { it.name == stored }) stored else effective?.name
            combo.selectedItem = preselect
            combo.isEnabled = true
        } else {
            combo.isEnabled = false
        }
        combo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JComponent
                val text = (value as? String)?.removeSuffix(WORKSPACE_SUFFIX) ?: ""
                comp.toolTipText = value as? String
                (comp as? javax.swing.JLabel)?.text = text
                return comp
            }
        }
        fileCombo = combo

        val description = buildString {
            append("<html>")
            append(NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.description"))
            append("<br>")
            if (files.isEmpty()) {
                append("&nbsp;&nbsp;<font color='red'>")
                append(NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.noFile"))
                append("</font><br>")
            } else {
                val currentName = effective?.name?.removeSuffix(WORKSPACE_SUFFIX)
                append("&nbsp;&nbsp;")
                append(NavifoxMessageBundle.message(
                    "settings.tools.MrWorkspaceView.currentUsing",
                    currentName ?: NavifoxMessageBundle.message("settings.tools.MrWorkspaceView.none"),
                ))
                append("<br>")
            }
            append("</html>")
        }

        val label = JBLabel(description)
        label.alignmentX = Component.LEFT_ALIGNMENT

        val row = JPanel(BorderLayout(8, 0))
        row.add(JBLabel(sourceLabel()), BorderLayout.WEST)
        row.add(combo, BorderLayout.CENTER)

        // 防止在设置面板里被纵向拉伸占满整页
        combo.maximumSize = Dimension(Int.MAX_VALUE, combo.preferredSize.height)
        row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
        row.alignmentX = Component.LEFT_ALIGNMENT

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(label)
        panel.add(Box.createVerticalStrut(8))
        panel.add(row)
        panel.add(Box.createVerticalGlue())
        return panel
    }

    override fun isModified(): Boolean {
        val files = findWorkspaceFiles(project)
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
        val selected = fileCombo?.selectedItem as? String
        getMrWorkspaceSettings(project).state.selectedWorkspaceFile = selected
        MrWorkspacePanes.refresh(project)
    }

    override fun reset() {
        val combo = fileCombo ?: return
        val files = findWorkspaceFiles(project)
        val stored = getMrWorkspaceSettings(project).state.selectedWorkspaceFile
        val preselect = if (stored != null && files.any { it.name == stored }) stored else resolveWorkspaceFile(files, stored)?.name
        combo.selectedItem = preselect
    }
}

/** 供气泡动作打开本设置页（伴生对象只允许常量，故置于顶层）。 */
internal fun openWorkspaceSettings(project: Project) {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, MrWorkspaceSettingsConfigurable::class.java)
}
