# 更新日志 Changelog

项目版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)进行定义；本篇日志遵循
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 所提出的约定，使用中英双语编写。

This file is written in both English and Chinese: project versions follow [Semantic Versioning](https://semver.org/),
and this changelog follows the conventions of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 新增

- 新增“自动隐藏当前工作区目录”选项（位于设置页“显示”分组）：勾选后，工作区中路径与当前项目根目录完全一致的顶层文件夹不再显示（按解析后的绝对路径判断，不依赖 `.`、`./` 等写法）。
- 隐藏该文件夹后，其子目录不再被“内容根去重”剪除，会照常出现在包含它的其它根目录下。
- 新增“不显示文件夹所在路径”选项（同样位于设置页“显示”分组，默认不勾选）：勾选后不再显示顶层文件夹名称后方的相对路径。

### Added

- Add a "Hide the current workspace directory automatically" option (in the "Display" group of the settings page): when enabled, a top-level folder whose resolved absolute path is exactly the current project root is no longer shown; the comparison does not rely on `.` or `./` spellings.
- Once that folder is hidden, its subdirectories are no longer pruned by content-root de-duplication and appear normally under the other roots that include them.
- Add a "Hide the Folder Location" option (in the "Display" group of the settings page, off by default): when enabled, the relative path shown as gray text right after each top-level folder name is no longer displayed.

### 修复

- 修复 VFS 自动刷新订阅的生命周期：订阅改随面板释放，不再挂在项目上，避免面板反复重建时监听器越积越多。
- 修复在“多根工作区”视图内使用原生“重构”（重命名、移动）以及新建、删除文件后目录树不自动刷新的问题：工作区根目录内的文件系统变化与配置文件的保存现在都会自动更新视图（350 毫秒防抖合并，效果等价于点刷新按钮）。
- 修复在“随处搜索”（双击 Shift）等入口选中**文件夹**后无法定位到“多根工作区”视图的问题：自定义目录节点现在与原生目录节点一样支持按 `VirtualFile` 匹配。

### Fixed

- Fix the lifetime of the VFS auto-refresh subscription: it is now disposed together with the view pane instead of the project, so listeners no longer pile up when the pane is recreated.
- Fix the tree not refreshing automatically after native refactoring (rename/move), file creation or deletion inside the "Workspace (Multi-Root)" view: file-system changes within workspace roots and configuration edits now refresh the view automatically (debounced by 350 ms, equivalent to pressing Refresh).
- Fix navigation to a **folder** (for example picked in Search Everywhere) failing to locate it in the "Workspace (Multi-Root)" view: custom directory nodes now match by `VirtualFile`, exactly like native directory nodes.

### 变更

- 调整“在此视图中选择”（Alt＋F1）的目标优先级：目标位于工作区根目录内时优先定位到“多根工作区”视图（双击 Shift 选中文件夹等自动定位同样生效）；工作区之外的路径仍由原生“项目”视图定位。
- 放宽兼容范围：由 2025.3 至 2026.1.*（内部构建 `253` 至 `261.*`）扩展为 2025.3 至 2026.2.*（内部构建 `253` 至 `262.*`）。

### Changed

- Change the "Select In" (Alt+F1) target precedence: files and folders inside workspace roots are now located in the "Workspace (Multi-Root)" view first (automatic navigation, such as picking a folder in Search Everywhere, behaves the same way); targets outside the workspace still open in the native Project view.
- Widen the compatibility range from 2025.3 through 2026.1.\* (internal builds `253` to `261.*`) to 2025.3 through 2026.2.\* (internal builds `253` to `262.*`).

## [0.1.1] - 2026-09-09

### 新增

- 新增插件图标，改编自[Catppuccin](https://github.com/catppuccin/vscode-icons/)。

### Added

- Add a dedicated plugin icon (adapted from the [Catppuccin](https://github.com/catppuccin/vscode-icons/) icon set) with separate light and dark variants for both IDE themes.

### 修复

- 解决插件更新日志提取不到英文部分的问题。

### Fixed

- Fix release-note extraction so English entries are no longer dropped: each language now lives in its own section, and both are picked up.

## [0.1.0] - 2026-09-08

### 新增

- 在“项目”工具窗口新增“多根工作区”面板，渲染 VS Code 风格的多根工作区（`.code-workspace`，JSONC）。
- 支持并列展示多个根目录并做内容根去重：同一物理文件只在所属最深的文件夹下出现一次。
- 缺失/无法解析的 folder 以警示节点展示；项目外目录仍可浏览（PSI 回退）。
- 保持与原生 Project 树一致的操作：右键菜单、拖拽、速度搜索、排序依据、Alt+F1“在视图中选择”定位。
- 无可用配置（无文件或全部解析失败）时显示空态覆盖层与“创建配置文件”按钮；创建走保存对话框并写入本地化模板后打开编辑。
- 新增设置页（工具 → 多根工作区视图）选择使用的配置文件；无配置时显示不落盘的“（无可用配置文件）”占位。
- 存在多个配置文件并自动选用一个时气泡提醒一次。
- 界面支持英文与简体中文（消息资源包）。

### Added

- Add a "Workspace (Multi-Root)" pane to the Project tool window that renders VS Code-style multi-root workspaces (`.code-workspace`, JSONC).
- Support multiple root directories in parallel with deduplication: every physical file appears only once, under its deepest owning folder.
- Show missing/unparseable folders as warning nodes and keep folders outside the project browsable (PSI fallback).
- Keep native Project-tree behavior: context menus, drag & drop, speed search, sorting options, and Alt+F1 "Select in" navigation.
- Add an empty-state overlay with a "Create Configuration File" button when no usable configuration exists; creation goes through a Save dialog and writes a localized `.code-workspace` template, then opens it in the editor.
- Add a Settings page (Tools -> Multi-Root Workspace View) to choose the configuration file; when none exists it shows a non-persisted "（无可用配置文件）" placeholder.
- Notify once when multiple configuration files exist and one is auto-picked.
- Support English and Simplified Chinese UI (message bundles).
