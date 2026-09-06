package net.navifox.plugins.core

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import java.io.File

/**
 * 单个 `*.code-workspace` 文件的解析结果。
 */
internal sealed class MrWorkspaceParseResult {

    /** 解析成功（可能零个文件夹，由 UI 决定如何提示）。 */
    class Usable(val folders: List<MrFolder>) : MrWorkspaceParseResult()

    /** 读取文件内容失败。 */
    class ReadFailed(val cause: Exception) : MrWorkspaceParseResult()

    /** JSONC 消毒后仍不是合法 JSON。 */
    class JsoncInvalid(val cause: Exception) : MrWorkspaceParseResult()

    /** 顶层 `"folders"` 存在但不是数组。 */
    object FoldersNotArray : MrWorkspaceParseResult()
}

/**
 * 解析单个 `*.code-workspace` 文件。
 *
 * - 先把内容经 [Jsonc] 规整成严格 JSON，再交给平台解析器。
 * - 顶层 `"folders"` 缺失与空数组一样按“可用空工作区”处理（不视为不可用）。
 */
internal fun parseWorkspace(file: VirtualFile): MrWorkspaceParseResult {
    val text = try {
        file.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        return MrWorkspaceParseResult.ReadFailed(e)
    }
    val root: Map<String, Any?> = try {
        JsonReaderEx(Jsonc.sanitize(text)).use { JsonUtil.nextObject(it) }
    } catch (e: Exception) {
        return MrWorkspaceParseResult.JsoncInvalid(e)
    }
    val rawFolders = root["folders"] ?: return MrWorkspaceParseResult.Usable(emptyList())
    if (rawFolders !is List<*>) {
        return MrWorkspaceParseResult.FoldersNotArray
    }
    val folders = rawFolders.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val path = map["path"] as? String ?: return@mapNotNull null
        MrFolder(map["name"] as? String, path, resolveFolder(file, path))
    }
    return MrWorkspaceParseResult.Usable(folders)
}

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
