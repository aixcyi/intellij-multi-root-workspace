package net.navifox.plugins.workspace

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.navifox.plugins.NavifoxMessageBundle
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox

/**
 * 设置页里“不再提醒有多个配置文件”复选框与项目级设置的往返：勾选能落盘、重开能回显、不误报为已修改。
 *
 * 气泡里的“不再提醒”链接写的是同一个字段（`neverNotifyMultipleWorkspaceFiles`），此处不重复覆盖。
 */
class MrWorkspaceSettingsConfigurableTest : BasePlatformTestCase() {

    private fun checkBoxes(root: Component): List<JCheckBox> {
        val found = mutableListOf<JCheckBox>()
        if (root is JCheckBox) found += root
        if (root is Container) root.components.forEach { found += checkBoxes(it) }
        return found
    }

    fun testNeverNotifyCheckBoxRoundTrip() {
        val configurable = MrWorkspaceSettingsConfigurable(project)
        val component = configurable.createComponent()
        configurable.reset()

        val label = NavifoxMessageBundle.message("settings.tools.MrWorkspace.neverNotifyMultipleWorkspaceFiles")
        val checkBox = checkBoxes(component).single { it.text == label }

        assertFalse("默认不勾选", checkBox.isSelected)
        assertFalse("刚创建时不应视为已修改", configurable.isModified())

        checkBox.isSelected = true
        assertTrue("勾选后应视为已修改", configurable.isModified())

        configurable.apply()
        assertTrue("勾选应写进项目级设置", getMrWorkspaceSettings(project).state.neverNotifyMultipleWorkspaceFiles)

        // 重新打开设置页：应回显已保存的值，并且不再算作已修改。
        val reopened = MrWorkspaceSettingsConfigurable(project)
        val reopenedComponent = reopened.createComponent()
        reopened.reset()
        val reopenedCheckBox = checkBoxes(reopenedComponent).single { it.text == label }
        assertTrue("重开后应保持勾选", reopenedCheckBox.isSelected)
        assertFalse("重开后不应视为已修改", reopened.isModified())
    }
}
