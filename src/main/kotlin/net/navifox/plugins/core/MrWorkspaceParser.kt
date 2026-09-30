package net.navifox.plugins.core

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * 单个 `*.code-workspace` 文件的解析结果。
 */
internal sealed class MrWorkspaceParseResult {

    /** 解析成功（可能零个文件夹，由 UI 决定如何提示）。 */
    class Usable(val folders: List<MrFolder>) : MrWorkspaceParseResult()

    /** 读取文件内容失败。 */
    class ReadFailed(val cause: Exception) : MrWorkspaceParseResult()

    /** JSONC 内容不是合法 JSON。 */
    class JsoncInvalid(val cause: Exception) : MrWorkspaceParseResult()

    /** 顶层 `"folders"` 存在但不是数组。 */
    object FoldersNotArray : MrWorkspaceParseResult()
}

/**
 * VS Code 的 `*.code-workspace` 实际是 JSONC：允许行注释与块注释，也常带尾逗号。
 *
 * 用平台内置的 `kotlinx-serialization-json` 解析（依赖见 `build.gradle.kts` 的 `bundledModule`），
 * 因此不必再自行消毒文本；唯一要手工处理的是 UTF-8 BOM——该库不接受以 BOM 开头的输入。
 *
 * 保持严格模式（不开启 `isLenient`）：未加引号的键与单引号字符串仍按非法处理，与 VS Code 写出的
 * 严格 JSON 一致，报错信息也比宽松模式更明确。
 */
@OptIn(ExperimentalSerializationApi::class)
private val JSONC = Json {
    allowComments = true
    allowTrailingComma = true
}

/** UTF-8 BOM：以文本方式读取文件时可能残留在首字符位置。 */
private const val BOM_PREFIX = "\uFEFF"

/**
 * 解析单个 `*.code-workspace` 文件。
 *
 * - 读取内容后交给 [parseWorkspaceText] 解析，再把 `folders[].path` 解析成 [VirtualFile]。
 */
internal fun parseWorkspace(file: VirtualFile): MrWorkspaceParseResult {
    val text = try {
        file.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        return MrWorkspaceParseResult.ReadFailed(e)
    }
    return when (val result = parseWorkspaceText(text)) {
        is MrWorkspaceParseResult.Usable -> MrWorkspaceParseResult.Usable(
            result.folders.map { it.copy(directory = resolveFolder(file, it.path)) }
        )

        else -> result
    }
}

/**
 * 把 JSONC 文本解析成文件夹列表（纯函数，不访问文件系统）。
 *
 * - 顶层 `"folders"` 缺失、为 `null` 或为空数组时，都按“可用的空工作区”处理（不视为不可用）；
 * - `folders` 中非对象、或缺少字符串 `path` 的条目一律跳过；`name` 非字符串时按未填写处理。
 */
internal fun parseWorkspaceText(text: String): MrWorkspaceParseResult {
    val root = try {
        JSONC.parseToJsonElement(text.removePrefix(BOM_PREFIX)).jsonObject
    } catch (e: Exception) {
        return MrWorkspaceParseResult.JsoncInvalid(e)
    }
    val rawFolders = root["folders"]?.takeIf { it !is JsonNull }
        ?: return MrWorkspaceParseResult.Usable(emptyList())
    if (rawFolders !is JsonArray) {
        return MrWorkspaceParseResult.FoldersNotArray
    }
    val folders = rawFolders.mapNotNull { item ->
        val entry = item as? JsonObject ?: return@mapNotNull null
        val path = entry.stringOrNull("path") ?: return@mapNotNull null
        MrFolder(entry.stringOrNull("name"), path, null)
    }
    return MrWorkspaceParseResult.Usable(folders)
}

/** 取字符串字段；非字符串（数字、布尔、`null`）一律按缺失处理，与 VS Code 的 schema 一致。 */
private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * 解析 `folders[].path` 对应的目录。
 *
 * VS Code 中相对 path 以 `.code-workspace` 文件所在目录为基准。
 */
internal fun resolveFolder(workspaceFile: VirtualFile, path: String): VirtualFile? {
    val target = resolveFolderPath(workspaceFile.path, path) ?: return null
    return LocalFileSystem.getInstance().findFileByIoFile(target)
}

/**
 * 计算 `folders[].path` 指向的本地文件：绝对路径直接用，相对路径以工作区文件所在目录为基准。
 *
 * 纯函数，便于单元测试。
 */
internal fun resolveFolderPath(workspaceFilePath: String, path: String): File? {
    val io = File(path)
    if (io.isAbsolute) return io
    val parent = File(workspaceFilePath).parentFile ?: return null
    return File(parent, path)
}
