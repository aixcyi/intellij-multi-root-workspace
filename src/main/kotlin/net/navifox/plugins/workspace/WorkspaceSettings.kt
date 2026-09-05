package net.navifox.plugins.workspace

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

internal const val WORKSPACE_SUFFIX = ".code-workspace"

/**
 * 项目级设置:记录用户在 Settings → Tools 中指定的 *.code-workspace 文件名。
 * 值为 null 表示自动检测(唯一文件直接使用;多个文件取文件名顺序第一个)。
 */
@Service(Service.Level.PROJECT)
@State(name = "MultiRootWorkspaceSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WorkspaceSettings : PersistentStateComponent<WorkspaceSettings.State> {

    class State {
        var selectedWorkspaceFile: String? = null
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}

/** 项目级服务通过 Project.getService 获取(ServiceManager 已在 Java 层废弃)。 */
internal fun getWorkspaceSettings(project: Project): WorkspaceSettings =
    project.getService(WorkspaceSettings::class.java)

/** 扫描项目根目录第一层的 *.code-workspace 文件(按文件名排序)。 */
internal fun findWorkspaceFiles(project: Project): List<VirtualFile> {
    val base = project.basePath ?: return emptyList()
    val dir = LocalFileSystem.getInstance().findFileByIoFile(File(base)) ?: return emptyList()
    return dir.children
        .filter { it.isValid && !it.isDirectory && it.name.endsWith(WORKSPACE_SUFFIX) }
        .sortedBy { it.name }
}

/**
 * 决定当前展示的 workspace 文件,优先级:
 * 1. 设置中指定且文件存在;
 * 2. 只有一个文件时自动使用;
 * 3. 有多个且未指定时按文件名顺序取第一个(是否提醒由调用方决定)。
 */
internal fun resolveWorkspaceFile(project: Project, files: List<VirtualFile>): VirtualFile? {
    if (files.isEmpty()) return null
    val settings = getWorkspaceSettings(project)
    settings.state.selectedWorkspaceFile?.let { name ->
        files.firstOrNull { it.name == name }?.let { return it }
    }
    return files.first()
}
