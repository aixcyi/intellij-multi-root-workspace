package net.navifox.plugins.workspace

/**
 * VS Code 的 *.code-workspace 实际是 JSONC:允许行注释与块注释,也常带尾逗号。
 * 先把内容规整成严格 JSON,再交给平台解析器,避免误报 "Invalid JSON"。
 */
object Jsonc {

    fun sanitize(text: String): String {
        val withoutBom = if (text.startsWith('\uFEFF')) text.substring(1) else text
        return stripTrailingCommas(stripComments(withoutBom))
    }

    /** 去除字符串字面量之外的 // 行注释与 /* ... */ 块注释。 */
    private fun stripComments(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var inString = false
        var escaped = false
        val len = text.length
        while (i < len) {
            val c = text[i]
            if (inString) {
                sb.append(c)
                if (escaped) {
                    escaped = false
                } else {
                    when (c) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
                i++
                continue
            }
            when {
                c == '"' -> {
                    inString = true
                    sb.append(c)
                    i++
                }

                c == '/' && i + 1 < len && text[i + 1] == '/' -> {
                    i += 2
                    while (i < len && text[i] != '\n') i++
                }

                c == '/' && i + 1 < len && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < len && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = minOf(i + 2, len)
                }

                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    /** 去除字符串字面量之外、后随 `}` 或 `]` 的尾逗号。 */
    private fun stripTrailingCommas(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var inString = false
        var escaped = false
        val len = text.length
        while (i < len) {
            val c = text[i]
            if (inString) {
                sb.append(c)
                if (escaped) {
                    escaped = false
                } else {
                    when (c) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                    }
                }
                i++
                continue
            }
            if (c == '"') {
                inString = true
                sb.append(c)
                i++
                continue
            }
            if (c == ',') {
                var j = i + 1
                while (j < len && text[j].isWhitespace()) j++
                if (j < len && (text[j] == '}' || text[j] == ']')) {
                    i++ // 丢弃尾逗号
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
