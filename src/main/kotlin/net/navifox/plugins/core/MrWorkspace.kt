package net.navifox.plugins.core

import com.intellij.openapi.vfs.VirtualFile

/**
 * 单个文件夹。
 *
 * - 详见 Visual Studio Code 的
 *   [Workspace file schema](https://code.visualstudio.com/docs/editing/workspaces/multi-root-workspaces#_workspace-file-schema)。
 *
 * @param name 文件夹名称。不填的时候默认为 [path] 的目录名称。
 * @param path 文件夹路径。可以是相对于 `*.code-workspace` 所在的目录，也可以是绝对路径。
 * @param directory 可以直接使用的 [VirtualFile] 实例；路径无法解析时为空。
 */
internal data class MrFolder(val name: String?, val path: String, val directory: VirtualFile?)

/**
 * 单个工作区：解析成功后的结果。
 *
 * @param file 内容来自哪个 `*.code-workspace` 文件。
 * @param folders 零个或多个文件夹。
 */
internal data class MrWorkspace(val file: VirtualFile, val folders: List<MrFolder>)

/**
 * 全部候选 `*.code-workspace` 文件都不可用时抛出。
 *
 * 抛出后由 UI 层捕获：不再渲染文件夹树，只在面板提示“配置文件不可用”。
 */
internal class MrWorkspaceUnusableException(message: String) : Exception(message)
