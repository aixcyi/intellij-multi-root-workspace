package net.navifox.plugins.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * [resolveFolderPath] 的纯函数测试（不依赖平台运行时）。
 * 注意：Windows 上 `C:/x` 才被认为是绝对路径，本用例面向本项目开发机（Windows）。
 */
class MrWorkspacePathTest {

    @Test
    fun relativePathResolvesAgainstWorkspaceFileParent() {
        val result = resolveFolderPath("C:/ws/app.code-workspace", "frontend")
        assertEquals(File("C:/ws/frontend").canonicalPath, result?.canonicalPath)
    }

    @Test
    fun nestedRelativePathKeepsSubPath() {
        val result = resolveFolderPath("C:/ws/sub/app.code-workspace", "a/b")
        assertEquals(File("C:/ws/sub/a/b").canonicalPath, result?.canonicalPath)
    }

    @Test
    fun absolutePathUsedAsIs() {
        val result = resolveFolderPath("C:/ws/app.code-workspace", "C:/proj/x")
        assertEquals(File("C:/proj/x").canonicalPath, result?.canonicalPath)
    }

    @Test
    fun workspaceFileWithoutParentCannotResolveRelative() {
        assertNull(resolveFolderPath("app.code-workspace", "frontend"))
    }

    /**
     * “当前工作区目录”（项目根）在 workspace 里可以用 `.`、`./`、`.` 加斜杠等多种写法声明，
     * 归一化后必须都指向同一个真实目录，才谈得上“完全一致”的比对。
     */
    @Test
    fun dotSpellingsResolveToWorkspaceDirectoryItself() {
        val expected = File("C:/proj").canonicalPath
        for (path in listOf(".", "./", ".//", "./.", "C:/proj/", "C:/proj")) {
            val result = resolveFolderPath("C:/proj/app.code-workspace", path)
            assertEquals("path=$path", expected, result?.canonicalPath)
        }
    }
}
