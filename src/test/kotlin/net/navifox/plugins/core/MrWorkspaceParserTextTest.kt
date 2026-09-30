package net.navifox.plugins.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [parseWorkspaceText] 的纯函数测试（不依赖平台运行时）。
 */
class MrWorkspaceParserTextTest {

    private fun foldersOf(text: String): List<MrFolder> {
        val result = parseWorkspaceText(text)
        assertTrue("期望 Usable，实际是 $result", result is MrWorkspaceParseResult.Usable)
        return (result as MrWorkspaceParseResult.Usable).folders
    }

    private fun failureOf(text: String): MrWorkspaceParseResult = parseWorkspaceText(text)

    @Test
    fun parsesCommentsAndTrailingCommas() {
        val text = """
            {
              // 顶层注释
              "folders": [
                { "name": "web", "path": "./web" }, // 行内注释
                /* 块注释 */
                { "name": "docs", "path": "./docs" },
              ],
            }
        """.trimIndent()
        val folders = foldersOf(text)
        assertEquals(2, folders.size)
        assertEquals("web", folders[0].name)
        assertEquals("./web", folders[0].path)
        assertEquals("docs", folders[1].name)
        assertEquals("./docs", folders[1].path)
    }

    @Test
    fun stripsBom() {
        val folders = foldersOf("\uFEFF{\"folders\":[{\"path\":\"./a\"}]}")
        assertEquals(1, folders.size)
        assertEquals("./a", folders[0].path)
    }

    @Test
    fun keepsCommentLikeContentInsideStrings() {
        val folders = foldersOf("{ \"folders\": [ { \"name\": \"say \\\"hi\\\" // x /* y */\", \"path\": \"./p\" } ] }")
        assertEquals("say \"hi\" // x /* y */", folders[0].name)
    }

    @Test
    fun folderWithoutStringPathIsSkipped() {
        val folders = foldersOf(
            """
                { "folders": [
                    { "name": "no-path" },
                    { "name": "numeric-path", "path": 42 },
                    { "name": "ok", "path": "./ok" },
                ] }
            """.trimIndent()
        )
        assertEquals(1, folders.size)
        assertEquals("ok", folders[0].name)
    }

    @Test
    fun nameFallsBackToNullWhenNotAString() {
        val folders = foldersOf("{ \"folders\": [ { \"name\": 42, \"path\": \"./a\" } ] }")
        assertNull(folders[0].name)
        assertEquals("./a", folders[0].path)
    }

    @Test
    fun nonObjectFolderEntryIsSkipped() {
        val folders = foldersOf("{ \"folders\": [ \"junk\", 7, null, { \"path\": \"./a\" } ] }")
        assertEquals(1, folders.size)
        assertEquals("./a", folders[0].path)
    }

    @Test
    fun missingOrEmptyOrNullFoldersAreUsableEmpty() {
        for (text in listOf("{}", "{ \"folders\": [] }", "{ \"folders\": null }")) {
            assertTrue("text=$text", foldersOf(text).isEmpty())
        }
    }

    @Test
    fun foldersNotArray() {
        assertEquals(MrWorkspaceParseResult.FoldersNotArray, failureOf("{ \"folders\": 42 }"))
        assertEquals(MrWorkspaceParseResult.FoldersNotArray, failureOf("{ \"folders\": {} }"))
    }

    @Test
    fun invalidJsonIsReported() {
        for (text in listOf("{ \"a\": }", "{ /* 未闭合", "[1, 2]", "// 只有注释")) {
            assertTrue("text=$text", failureOf(text) is MrWorkspaceParseResult.JsoncInvalid)
        }
    }

    @Test
    fun unquotedKeysAndSingleQuotesRemainInvalid() {
        assertTrue(failureOf("{ folders: [] }") is MrWorkspaceParseResult.JsoncInvalid)
        assertTrue(failureOf("{ 'folders': [] }") is MrWorkspaceParseResult.JsoncInvalid)
    }
}
