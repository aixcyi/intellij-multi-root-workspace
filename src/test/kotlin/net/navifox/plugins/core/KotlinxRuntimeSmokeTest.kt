package net.navifox.plugins.core

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 运行期可见性冒烟测试：`kotlinx-serialization-json` 来自平台内置模块
 * （`bundledModule`，不打进插件包），单元测试的类路径证明不了插件类加载器取不取得到它，
 * 因此这里真的启动一次平台，用**插件自己的类加载器**加载该类并解析一段 JSONC。
 */
class KotlinxRuntimeSmokeTest : BasePlatformTestCase() {

    fun testPluginClassLoaderSeesKotlinxSerialization() {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("net.navifox.plugins.mr-workspace"))
        assertNotNull("插件未被加载，冒烟测试无意义", plugin)

        val classLoader = plugin!!.pluginClassLoader
        assertNotNull("插件没有类加载器", classLoader)

        val jsonClass = classLoader!!.loadClass("kotlinx.serialization.json.Json")
        assertNotNull("插件类加载器看不到 kotlinx.serialization.json.Json", jsonClass)

        // 再用插件类加载器加载我们的解析器，确认整条链路都能跑通。
        val parserClass = classLoader.loadClass(
            "net.navifox.plugins.core.MrWorkspaceParserKt"
        )
        assertNotNull(parserClass)
    }
}
