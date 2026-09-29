package net.navifox.plugins.workspace

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * 项目级设置：记录用户在 Settings → Tools 中指定的 `*.code-workspace` 文件名。
 * 值为 `null` 表示自动检测（唯一文件直接使用；多个文件取文件名顺序第一个）。
 *
 * 文件扫描、选定与顺延解析规则见 `net.navifox.plugins.core` 工具包。
 */
@Service(Service.Level.PROJECT)
@State(name = "MultiRootWorkspaceSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class MrWorkspaceSettings : PersistentStateComponent<MrWorkspaceSettings.State> {

    class State {
        var selectedWorkspaceFile: String? = null

        /** 自动隐藏与“当前工作区路径”（项目根目录）完全一致的顶层文件夹。默认不隐藏。 */
        var hideWorkspaceDirectory: Boolean = false
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}

/** 项目级服务通过 Project.getService 获取（ServiceManager 已在 Java 层废弃）。 */
internal fun getMrWorkspaceSettings(project: Project): MrWorkspaceSettings =
    project.getService(MrWorkspaceSettings::class.java)
