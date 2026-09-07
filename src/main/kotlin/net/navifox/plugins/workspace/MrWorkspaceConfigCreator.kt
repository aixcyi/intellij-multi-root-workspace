package net.navifox.plugins.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import net.navifox.plugins.NavifoxMessageBundle
import net.navifox.plugins.core.WORKSPACE_SUFFIX
import java.io.File

/**
 * 新建一个 `*.code-workspace` 配置（模仿 VS Code 的“另存为”流程，是否落盘由用户决定）：
 *
 * 1. 弹出保存位置对话框，默认位置为项目根目录、默认文件名为 `<工程名>.code-workspace`；
 * 2. 确认后若文件尚不存在则写入模板并创建，已存在则原样打开（不覆盖内容）；
 * 3. 在编辑器中打开该文件供用户继续编辑；
 * 4. 刷新所有已创建的“多根工作区”面板。
 *
 * 模板内容见 [workspaceFileTemplate]（folders 的 `name` 随语言包本地化）。
 *
 * @param onCreated 成功创建/定位到文件后回调（设置页借此把下拉框切到新文件）。
 */
internal fun createWorkspaceConfig(project: Project, onCreated: (VirtualFile) -> Unit = {}) {
    if (!ApplicationManager.getApplication().isDispatchThread) {
        ApplicationManager.getApplication().invokeLater { createWorkspaceConfig(project, onCreated) }
        return
    }
    val basePath = project.basePath ?: return
    val baseDir = LocalFileSystem.getInstance().findFileByIoFile(File(basePath)) ?: return
    val suggestedName = File(basePath).name + WORKSPACE_SUFFIX

    val descriptor = FileSaverDescriptor(
        NavifoxMessageBundle.message("workspace.createConfig.dialog.title"),
        NavifoxMessageBundle.message("workspace.createConfig.dialog.description"),
        WORKSPACE_SUFFIX.removePrefix("."),
    )
    val wrapper = FileChooserFactory.getInstance()
        .createSaveFileDialog(descriptor, project)
        .save(baseDir, suggestedName) ?: return // 用户取消

    val chosen = wrapper.file
    val target = if (chosen.name.endsWith(WORKSPACE_SUFFIX)) {
        chosen
    } else {
        File(chosen.parentFile, chosen.name + WORKSPACE_SUFFIX)
    }
    val file = writeTemplateIfNew(project, target) ?: return
    FileEditorManager.getInstance(project).openFile(file, true)
    MrWorkspacePanes.refresh(project)
    onCreated(file)
}

/** 目标文件不存在则创建并写入模板；已存在则原样返回（不覆盖，避免误伤已有配置）。 */
private fun writeTemplateIfNew(project: Project, target: File): VirtualFile? {
    LocalFileSystem.getInstance().findFileByIoFile(target)?.let { existing ->
        if (existing.exists()) return existing
    }
    val content = workspaceFileTemplate().toByteArray(Charsets.UTF_8)
    return try {
        WriteCommandAction.writeCommandAction(project).compute<VirtualFile?, Exception> {
            val dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.parentFile ?: return@compute null)
                ?: return@compute null
            val vf = dir.findChild(target.name) ?: dir.createChildData(project, target.name)
            vf.setBinaryContent(content)
            vf
        }
    } catch (e: Exception) {
        LOG.warn("创建 *.code-workspace 失败：${target.path}（${e.message}）")
        null
    }
}

/** 新文件模板：与 VS Code 一致只有单个根文件夹，name 随语言包本地化，保留尾逗号以便符合手工编辑习惯。 */
private fun workspaceFileTemplate(): String {
    val folderName = NavifoxMessageBundle.message("workspace.createConfig.folderName")
    return """{
    "folders": [
        { "name": "$folderName", "path": "./" },
    ]
}"""
}

private val LOG = Logger.getInstance("net.navifox.plugins.workspace.MrWorkspaceConfigCreator")
