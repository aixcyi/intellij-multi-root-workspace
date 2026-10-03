package net.navifox.plugins.workspace

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.MrFolder
import net.navifox.plugins.core.MrWorkspaceLoadResult
import net.navifox.plugins.core.loadWorkspace
import net.navifox.plugins.core.resolveFolderPath
import java.io.File

/**
 * 顶层文件夹的**唯一真相**：树节点与所有“文件夹”菜单（工具窗口头部子菜单、Alt＋F2 弹窗、
 * 主工具栏小组件）都从这里取数据，因此不会出现“菜单里有、树里却没有”的错位。
 *
 * @param key 选择键（[folderSelectionKeyOf]，正斜杠绝对规范化路径）：既是菜单项的标识，也是过滤依据。
 * @param resolved 解析后的真实路径（`File`）；path 指向的目录不存在或无法解析时为空。
 * @param directory 解析后的 [VirtualFile]；没有解析或 VFS 里找不到时为空（树里显示警示节点）。
 * @param displayName 显示名：优先 `folders[].name`，否则目录名。
 * @param locationText 名称后方的灰色路径文本（或“当前工作区”标记）；按“不显示文件夹所在路径”设置可以为空。
 */
internal class MrFolderLeaf(
    val key: String,
    val resolved: File?,
    val directory: VirtualFile?,
    val displayName: String,
    val locationText: String?,
)

/**
 * 一次“加载 + 整理”的结果。
 *
 * @param workspacePath `*.code-workspace` 文件的路径（`folders[].path` 的相对基准）。
 * @param folders 实际参与渲染的文件夹（已含“强制显示当前工作区目录”补齐的那个）。
 * @param leaves 当前**实际可见**的顶层文件夹（去重、“自动隐藏当前工作区目录”之后）；路径解析不到的也在其中。
 */
internal class MrWorkspaceFolderSnapshot(
    val workspacePath: String,
    val folders: List<MrFolder>,
    val leaves: List<MrFolderLeaf>,
)

/**
 * 加载当前项目的工作区并整理出快照；**不依赖面板是否已建好树**。
 *
 * @return null 表示项目根目录没有任何 `*.code-workspace` 文件。
 * @throws net.navifox.plugins.core.MrWorkspaceUnusableException 全部候选文件都不可用时（含义与 [loadWorkspace] 一致）。
 */
internal fun loadFolderSnapshot(project: Project): MrWorkspaceFolderSnapshot? {
    val loaded = loadWorkspace(project, getMrWorkspaceSettings(project).state.selectedWorkspaceFile) ?: return null
    return folderSnapshot(project, loaded)
}

/** 把已加载的工作区整理成快照（[loadFolderSnapshot] 与面板的树共用同一套整理逻辑）。 */
internal fun folderSnapshot(project: Project, loaded: MrWorkspaceLoadResult): MrWorkspaceFolderSnapshot {
    val workspacePath = loaded.workspace.file.path
    val folders = effectiveFolders(project, workspacePath, loaded.workspace.folders)
    return MrWorkspaceFolderSnapshot(
        workspacePath = workspacePath,
        folders = folders,
        leaves = visibleFolderLeaves(project, workspacePath, folders),
    )
}

/**
 * 实际参与渲染与定位的顶层文件夹列表。
 *
 * 勾选“强制显示当前工作区目录”且配置文件**没有**声明与项目根相同的 folder 时，在**尾部**补一个：
 * 名字回退为目录名，位置文本由“当前工作区”标记给出。补出来的条目与显式声明的完全等价——同样进入
 * 内容根去重，也会计入供定位使用的工作区根目录。
 */
private fun effectiveFolders(project: Project, workspacePath: String, folders: List<MrFolder>): List<MrFolder> {
    if (!getMrWorkspaceSettings(project).state.forceShowWorkspaceDirectory) return folders
    val basePathKey = projectBasePathKey(project) ?: return folders
    val declared = folders.any { folder ->
        resolveFolderPath(workspacePath, folder.path)?.let { absolutePathKey(it) == basePathKey } == true
    }
    if (declared) return folders
    val base = project.basePath ?: return folders
    val directory = LocalFileSystem.getInstance().findFileByIoFile(File(base)) ?: return folders
    return folders + MrFolder(name = null, path = base, directory = directory)
}

/**
 * 当前**实际渲染**的顶层文件夹（“自动隐藏当前工作区目录”“相同 path 只保留首个”之后的可见集合）。
 *
 * 树与各处菜单都以这份列表为准，因此它们永远一致。
 * 路径无法解析的 folder 保留在列表中（树里仍显示警示节点），但菜单会跳过它们——见 [folderLeaf]。
 */
private fun visibleFolderLeaves(
    project: Project,
    workspacePath: String,
    folders: List<MrFolder>,
): List<MrFolderLeaf> {
    val settingsState = getMrWorkspaceSettings(project).state
    // 相同目录（按解析后的真实路径比较，与 VS Code 一致）只保留首个声明。
    val seen = HashSet<String>()
    val uniqueFolders = folders.filter { folder -> seen.add(folderIdentityKey(workspacePath, folder.path)) }
    // “自动隐藏当前工作区目录”：按解析后的绝对规范化路径与项目根目录比对（不看 `.`／`./` 写法）。
    val basePathKey = projectBasePathKey(project)
    // “自动隐藏”与“强制显示”在设置页里互斥；若状态里两者同时为 true（手改过文件），
    // 按“强制显示”优先——此时不隐藏，必要的话还会补上缺失的当前工作区目录。
    val hideWorkspaceDirectory =
        settingsState.hideWorkspaceDirectory && !settingsState.forceShowWorkspaceDirectory
    return uniqueFolders.mapNotNull { folder ->
        val resolved = resolveFolderPath(workspacePath, folder.path)
        val hidden = hideWorkspaceDirectory && basePathKey != null && resolved != null &&
            absolutePathKey(resolved) == basePathKey
        if (hidden) null else folderLeaf(project, folder, resolved)
    }
}

/**
 * 把单个 folder 整理成展示信息（[visibleFolderLeaves] 去重后再调用它）。
 *
 * @param resolved 解析后的真实路径（由调用方解析后传入）。
 */
private fun folderLeaf(project: Project, folder: MrFolder, resolved: File?): MrFolderLeaf {
    // “不显示文件夹所在路径”：关掉时不给节点传路径文本（节点 update 里据此不设置 location）；
    // “当前工作区”标记占的是同一个位置，同样受它控制。
    val showFolderPath = getMrWorkspaceSettings(project).state.showFolderPath
    val directory = folder.directory
    if (directory == null) {
        // path 不存在或无法解析：仍显示顶层文件夹（文件夹图标叠警示角标），标题用 name 或目录名回退。
        // 它没有可比较的目录身份，因此不进任何“文件夹”菜单（调用方会跳过 [resolved] 为空的项）。
        return MrFolderLeaf(
            key = resolved?.let(::folderSelectionKeyOf) ?: normalizeFolderPathKey(folder.path),
            resolved = resolved,
            directory = null,
            displayName = folder.name ?: folderPathBasename(folder.path),
            locationText = normalizedPathText(folder.path).takeIf { showFolderPath },
        )
    }
    // 与“当前工作区路径”（项目根）重合的那个 folder 没有相对路径可显示，改用“当前工作区”标记。
    // 它与普通路径文本占同一个位置，因此同样受“不显示文件夹所在路径”选项控制。
    val basePathKey = projectBasePathKey(project)
    val currentWorkspace = basePathKey != null && absolutePathKey(File(directory.path)) == basePathKey
    val locationText = when {
        !showFolderPath -> null
        currentWorkspace -> NavifoxMessageBundle.message("MrWorkspacePane.topFolder.currentWorkspace")
        else -> relativeLocationText(project, directory)
    }
    return MrFolderLeaf(
        key = folderSelectionKey(directory),
        resolved = resolved,
        directory = directory,
        // 显示名优先取 workspace 中的 name，否则回退到目录名；顺序保持 folders 数组顺序。
        displayName = folder.name ?: directory.name,
        locationText = locationText,
    )
}

/** 项目根目录（“当前工作区”）的绝对规范化路径键；没有 `basePath` 时为空。 */
private fun projectBasePathKey(project: Project): String? {
    val base = project.basePath ?: return null
    return absolutePathKey(File(base))
}

/** 相同 path 的去重键：反斜杠归一为正斜杠、去掉前导 `./` 与尾部斜杠。 */
private fun normalizeFolderPathKey(path: String): String =
    path.replace('\\', '/').removePrefix("./").removeSuffix("/")

/**
 * folder 的去重键：优先用“解析后的真实目录路径”（相对 workspace 文件解析并归一化），使
 * `../navifox-pages/apps/hei` 与 `./apps/hei` 这类指向同一目录的写法合并；无法解析时退回
 * 字符串规范化键。
 */
private fun folderIdentityKey(workspaceFilePath: String, path: String): String {
    val resolved = resolveFolderPath(workspaceFilePath, path)
    return if (resolved != null) {
        absolutePathKey(resolved)
    } else {
        normalizeFolderPathKey(path)
    }
}

/** 用于展示的路径文本：反斜杠归一、去掉前导 `./` 与尾部斜杠；根（`.` 或空）返回 null。 */
private fun normalizedPathText(path: String): String? {
    val normalized = normalizeFolderPathKey(path)
    return normalized.takeIf { it.isNotEmpty() && it != "." }
}

/** 从 path 中解析目录名（不依赖文件系统，用于目录不存在时的标题回退）。 */
private fun folderPathBasename(path: String): String {
    val trimmed = normalizeFolderPathKey(path).trimEnd('/').ifEmpty { return path }
    val last = trimmed.substringAfterLast('/').ifEmpty { trimmed }
    return last.takeIf { it.isNotEmpty() && it != "." } ?: path
}

/**
 * 顶层标题里跟在名称后方的路径文本（灰色小字）：以项目根（深度 0）为基准 —— 项目内显示相对路径；
 * 项目外（上级目录）为避免前置一串 `../`，直接显示绝对路径；与根重合时返回 null 不显示。
 */
private fun relativeLocationText(project: Project, directory: VirtualFile): String? {
    val base = project.basePath ?: return normalizedPathText(directory.path)
    return try {
        val basePath = java.nio.file.Paths.get(base)
        val dirPath = java.nio.file.Paths.get(directory.path)
        val relative = basePath.relativize(dirPath).toString().replace('\\', '/')
        when {
            relative.isEmpty() || relative == "." -> null
            relative == ".." || relative.startsWith("../") -> directory.path.replace('\\', '/')
            else -> relative
        }
    } catch (_: Exception) {
        normalizedPathText(directory.path)
    }
}

/**
 * 把 [File] 转成**绝对规范化路径键**：正斜杠、已解析 `.`／`..`、绝对路径。
 *
 * 之所以不能直接用 [java.io.File.getPath]：Windows 上它是**反斜杠**，而 [VirtualFile.getPath] 一律是
 * **正斜杠**，两者直接比较永远不相等（历史缺陷：内容根去重曾因此在 Windows 上完全失效）。
 * 凡是拿路径当“目录身份”比较的地方（去重、剪枝、与项目根比对）都必须先过这个函数。
 */
internal fun absolutePathKey(file: File): String =
    file.toPath().normalize().toAbsolutePath().toString().replace('\\', '/')

/**
 * “文件夹”菜单里一个文件夹的**选择键**：正斜杠的绝对规范化路径。
 *
 * [VirtualFile.getPath] 本身就是正斜杠绝对路径；这里再走一遍 [absolutePathKey]，是为了和
 * 其它按路径比较的地方（内容根去重、与项目根比对）保持同一套写法。
 */
internal fun folderSelectionKey(directory: VirtualFile): String = absolutePathKey(File(directory.path))

/** 与 [folderSelectionKey] 同理，只是输入来自已解析的 [File]（内容根去重与剪枝用的也是这个键）。 */
internal fun folderSelectionKeyOf(file: File): String = absolutePathKey(file)
