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
}
