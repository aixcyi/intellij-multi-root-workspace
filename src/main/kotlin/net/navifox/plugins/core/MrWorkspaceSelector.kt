package net.navifox.plugins.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

internal const val WORKSPACE_SUFFIX = ".code-workspace"

/**
 * 扫描项目根目录第一层的 `*.code-workspace` 文件（按文件名排序）。
 */
internal fun findWorkspaceFiles(project: Project): List<VirtualFile> {
    val base = project.basePath ?: return emptyList()
    val dir = LocalFileSystem.getInstance().findFileByIoFile(File(base)) ?: return emptyList()
    return dir.children
        .filter { it.isValid && !it.isDirectory && it.name.endsWith(WORKSPACE_SUFFIX) }
        .sortedBy { it.name }
}

/**
 * 决定“目标”的 workspace 文件（只做选定，不做解析）。优先使用设置中指定且存在的文件；
 * 否则只有一个文件时自动使用该文件；多个文件且未指定时按文件名顺序取第一个。
 *
 * @param files 已按文件名排序的候选文件（通常来自 [findWorkspaceFiles]）。
 * @param preferredName 用户在设置中指定的文件名，没有则为 `null`。
 */
internal fun resolveWorkspaceFile(files: List<VirtualFile>, preferredName: String?): VirtualFile? {
    if (files.isEmpty()) return null
    if (preferredName != null) {
        files.firstOrNull { it.name == preferredName }?.let { return it }
    }
    return files.first()
}

/**
 * 解析失败时的顺延候选顺序：设置中指定者优先（文件仍存在时），其余按文件名序。
 */
internal fun orderedCandidates(files: List<VirtualFile>, preferredName: String?): List<VirtualFile> {
    if (files.isEmpty() || preferredName == null) return files
    val preferred = files.firstOrNull { it.name == preferredName } ?: return files
    return listOf(preferred) + files.filter { it !== preferred }
}

/**
 * [loadWorkspace] 成功时的结果。
 *
 * @param workspace 解析成功的工作区。
 * @param candidates 本次参与选定的全部候选文件（即扫描结果），供 UI 做自动选择提醒。
 */
internal class MrWorkspaceLoadResult(
    val workspace: MrWorkspace,
    val candidates: List<VirtualFile>,
)

/**
 * 找到可用工作区并完成解析。
 *
 * 顺序：[orderedCandidates] 依次尝试，某文件解析失败（读取失败、JSONC 非法或 `"folders"` 不是数组）
 * 时自动顺延到下一个候选；全部不可用时抛出 [MrWorkspaceUnusableException]。
 *
 * @return null 表示项目根目录没有任何 `*.code-workspace` 文件。
 */
internal fun loadWorkspace(project: Project, preferredName: String?): MrWorkspaceLoadResult? {
    val files = findWorkspaceFiles(project)
    if (files.isEmpty()) return null
    val failures = mutableListOf<String>()
    for (file in orderedCandidates(files, preferredName)) {
        when (val result = parseWorkspace(file)) {
            is MrWorkspaceParseResult.Usable ->
                return MrWorkspaceLoadResult(MrWorkspace(file, result.folders), files)

            is MrWorkspaceParseResult.ReadFailed ->
                failures += "${file.name}：读取失败（${describe(result.cause)}）"

            is MrWorkspaceParseResult.JsoncInvalid ->
                failures += "${file.name}：不是合法的 JSONC（${describe(result.cause)}）"

            MrWorkspaceParseResult.FoldersNotArray ->
                failures += "${file.name}：“folders” 不是数组"
        }
    }
    throw MrWorkspaceUnusableException(failures.joinToString("；"))
}

private fun describe(cause: Exception): String = cause.message ?: cause::class.simpleName ?: "未知原因"
