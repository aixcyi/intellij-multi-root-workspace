package net.navifox.plugins.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JsoncTest {

    @Test
    fun stripsBom() {
        assertEquals("{\"a\":1}", Jsonc.sanitize("\uFEFF{\"a\":1}"))
    }

    @Test
    fun removesLineCommentsButKeepsInsideStrings() {
        val input = "{\n  // line\n  \"a\": \"x//y\",\n  \"b\": 1 // trailing\n}"
        val expected = "{\n  \n  \"a\": \"x//y\",\n  \"b\": 1 \n}"
        assertEquals(expected, Jsonc.sanitize(input))
    }

    @Test
    fun removesBlockComments() {
        val input = "{\"folders\": [\n  {\"path\": \"./a\" /* note */},\n  {\"path\": \"./b\"},\n]}"
        val expected = "{\"folders\": [\n  {\"path\": \"./a\" },\n  {\"path\": \"./b\"}\n]}"
        assertEquals(expected, Jsonc.sanitize(input))
    }

    @Test
    fun keepsCommentLikeContentInsideStrings() {
        // 字符串内的转义引号、// 与 /* */ 都不得被当作注释处理。
        val input = "{ \"s\": \"say \\\"hi\\\" // x /* y */\" }"
        assertEquals(input, Jsonc.sanitize(input))
    }

    @Test
    fun stripsTrailingCommasBeforeClosingBrackets() {
        assertEquals("{\"a\":[1,2],\"b\":3}", Jsonc.sanitize("{\"a\":[1,2,],\"b\":3,}"))
    }

    @Test
    fun typicalCodeWorkspaceBecomesStrictJson() {
        val input = """
            {
              // 顶层注释
              "folders": [
                { "name": "web", "path": "./web" }, // 行内注释
                { "name": "docs", "path": "./docs" },
              ],
            }
        """.trimIndent()
        val sanitized = Jsonc.sanitize(input)
        assertFalse(sanitized.contains("//"))
        assertFalse(sanitized.contains("/*"))
        assertFalse(sanitized.contains(",]"))
        assertFalse(sanitized.contains(",}"))
        assertFalse(sanitized.contains("\uFEFF"))
    }
}
