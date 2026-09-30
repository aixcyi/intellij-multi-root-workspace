package net.navifox.plugins.workspace

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.navifox.plugins.NavifoxMessageBundle
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox

/**
 * 设置页复选框与项目级设置的往返：勾选能落盘、重开能回显、不误报为已修改，
 * 以及“自动隐藏当前工作区目录”与“强制显示当前工作区目录”的互斥。
 *
 * 气泡里的“不再提醒”链接写的是同一个字段（`neverNotifyMultipleWorkspaceFiles`），此处不重复覆盖。
 */
class MrWorkspaceSettingsConfigurableTest : BasePlatformTestCase() {

    private fun label(key: String): String = NavifoxMessageBundle.message(key)

    private fun checkBoxes(root: Component): List<JCheckBox> {
        val found = mutableListOf<JCheckBox>()
        if (root is JCheckBox) found += root
        if (root is Container) root.components.forEach { found += checkBoxes(it) }
        return found
    }

    private fun checkBox(component: Component, key: String): JCheckBox =
        checkBoxes(component).single { it.text == label(key) }

    fun testNeverNotifyCheckBoxRoundTrip() {
        val configurable = MrWorkspaceSettingsConfigurable(project)
        val component = configurable.createComponent()
        configurable.reset()

        val key = "settings.tools.MrWorkspace.neverNotifyMultipleWorkspaceFiles"
        val checkBox = checkBox(component, key)

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
        assertTrue("重开后应保持勾选", checkBox(reopenedComponent, key).isSelected)
        assertFalse("重开后不应视为已修改", reopened.isModified())
    }

    fun testHideAndForceShowWorkspaceDirectoryAreMutuallyExclusive() {
        val configurable = MrWorkspaceSettingsConfigurable(project)
        val component = configurable.createComponent()
        configurable.reset()

        val hide = checkBox(component, "settings.tools.MrWorkspace.hideWorkspaceDirectory")
        val forceShow = checkBox(component, "settings.tools.MrWorkspace.forceShowWorkspaceDirectory")

        hide.isSelected = true
        assertTrue(hide.isSelected)
        assertFalse("勾选“自动隐藏”后，“强制显示”应被取消", forceShow.isSelected)

        forceShow.isSelected = true
        assertTrue(forceShow.isSelected)
        assertFalse("勾选“强制显示”后，“自动隐藏”应被取消", hide.isSelected)

        forceShow.isSelected = false
        assertFalse("二者都不勾选是允许的（自动隐藏）", hide.isSelected)
        assertFalse("二者都不勾选是允许的（强制显示）", forceShow.isSelected)

        forceShow.isSelected = true
        configurable.apply()
        val state = getMrWorkspaceSettings(project).state
        assertTrue("应落盘为强制显示", state.forceShowWorkspaceDirectory)
        assertFalse("互斥项应同时落盘为未勾选", state.hideWorkspaceDirectory)
    }
}
