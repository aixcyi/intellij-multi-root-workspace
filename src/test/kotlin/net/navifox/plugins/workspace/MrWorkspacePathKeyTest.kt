package net.navifox.plugins.workspace

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [absolutePathKey] 必须与 `VirtualFile.path` 处在同一个“路径空间”（绝对、正斜杠、已解析 `.`／`..`），
 * 否则内容根去重（剪枝）在 Windows 上会静默失效 —— 2026-09-30 修过一次这个问题。
 *
 * 注意：本用例面向本项目开发机（Windows），与 `core/MrWorkspacePathTest` 的取向一致。
 */
class MrWorkspacePathKeyTest {

    @Test
    fun resolvesDotAndDotDotSegments() {
        assertEquals(
            "C:/ws/app/apps/web-antd",
            absolutePathKey(File("C:/ws/app/./apps/../apps/web-antd")),
        )
    }

    @Test
    fun usesForwardSlashesLikeVirtualFilePaths() {
        // VirtualFile.path 形如 `C:/ws/app/apps`：绝对、正斜杠、不含 `.`／`..`。
        assertEquals("C:/ws/app/apps", absolutePathKey(File("C:\\ws\\app\\apps")))
        assertEquals("C:/ws/app/apps", absolutePathKey(File("C:/ws/app/apps")))
    }
}
