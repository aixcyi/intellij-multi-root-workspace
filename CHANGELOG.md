# 更新日志 Changelog

项目版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)进行定义；本篇日志遵循
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 所提出的约定，使用中英双语编写。

This file is written in both English and Chinese: project versions follow [Semantic Versioning](https://semver.org/),
and this changelog follows the conventions of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 新增

- 新增“不再提醒有多个 *.code-workspace 文件可以切换”设置项；自动选用文件时弹出的气泡提示里也加了“不再提醒”链接，点一下即勾选该设置，之后不再提示。
- 设置页“配置源”下方新增一行小字说明，介绍 `*.code-workspace` 的格式与来源，并附上 VS Code 官方文档链接。
- 顶层文件夹中与当前工作区路径（项目根）相同的那一个，现在会在名称后方显示灰色的“当前工作区”标记（此前该位置为空）。
- “显示”分组新增“强制显示当前工作区目录”复选框：勾选后，若配置文件没有声明当前工作区目录，插件会自动补一个文件夹放到列表尾部；它与“自动隐藏当前工作区目录”互斥，勾选一个会自动取消另一个，二者也可以都不勾选。
- 新增“文件夹”菜单（工具窗口头部的子菜单，快捷键 **Alt＋F2**）：可以选择只看某一个顶层文件夹（其余顶层文件夹全部隐藏，并自动展开该目录），也可以选最前面的“显示所有文件夹”恢复原状；菜单项与 Alt＋F1“在…中选中”一样带 `1`~`9`、`0`、`A`~`Z` 的快速选择编号。该选择只记在内存中，重启 IDE 或重新打开项目后恢复为“显示所有”；菜单里当前正在显示的那一项带勾选标记，未过滤时勾在“显示所有文件夹”上。
- 主工具栏（New UI）左侧新增“文件夹”小组件（紧邻 Project Widget）：平时显示当前只看哪个顶层文件夹（“显示所有”时显示“所有文件夹”），点开后是与工具窗口头部完全相同的文件夹列表，选中一个即激活“项目”工具窗口并切换到“多根工作区”视图、只看该文件夹。项目里没有任何 `*.code-workspace` 文件时该小组件不出现。
- 设置页“显示”分组新增“隐藏主工具栏上的‘文件夹’小组件”，用不上这个入口的项目可以把它彻底关掉。

### Added

- Add a "Do not remind me about multiple *.code-workspace files" option; the balloon shown when a file is picked automatically now offers a "Do not remind me again" link that enables the option and stops further reminders.
- Add a short note below the workspace source file selector describing the `*.code-workspace` format and origin, with a link to the VS Code documentation.
- The top-level folder matching the current workspace path (the project root) now shows a gray "Current Workspace" label after its name instead of nothing.
- Add a "Force the 'Current Workspace' directory to be shown" option to the "Display" group: when enabled and the workspace file does not declare the current workspace directory, a folder for it is appended at the end of the list. It is mutually exclusive with "Hide the 'Current Workspace' directory automatically" (checking one clears the other; both may stay unchecked).
- Add a "Folders" menu (a submenu in the tool window header, shortcut **Alt+F2**): pick a single top-level folder to show (all other top-level folders are hidden and the picked directory is expanded), or pick "Show all folders" at the top to restore everything. Like the Alt+F1 "Select In" menu, its items carry `1`~`9`, `0`, `A`~`Z` quick-select numbering. The selection is kept in memory only and resets to "Show all folders" after an IDE restart or when the project is reopened; the entry currently being shown carries a checkmark, and while nothing is filtered the checkmark sits on "Show all folders".
- Add a "Folders" widget to the left side of the main toolbar (New UI), right next to the Project widget: it shows which top-level folder is currently being shown (or "All folders"), and opening it lists exactly the same folders as the tool window header. Picking one activates the Project tool window, switches to the "Workspace (Multi-Root)" view and shows only that folder. The widget is not shown when the project has no `*.code-workspace` file.
- Add a "Hide the 'Folders' widget in the main toolbar" option to the "Display" group of the settings page, for projects that do not need this shortcut at all.

### 修复

- 修正通知组 ID 与 `plugin.xml` 中注册值不一致的问题：此前项目根目录下存在多个 `*.code-workspace` 文件时，自动选用文件的气泡提示不会出现（该分支使用了未注册的通知组，会抛出异常）。
- 修复“内容根去重”在 Windows 上的失效：剪枝比较此前混用了反斜杠（`java.io.File.path`）与正斜杠（`VirtualFile.path`）两种写法，导致已被另一个工作区文件夹覆盖的子树仍会完整展开；现在统一按绝对规范化路径比较，被覆盖的目录只留下一个不可展开的文件夹节点。

### Fixed

- Fix the notification group ID not matching the value registered in `plugin.xml`: the balloon that reports which `*.code-workspace` file was picked automatically never appeared when the project root contained several of them (that branch used an unregistered notification group and threw an exception).
- Fix content-root de-duplication on Windows: the pruning comparison mixed backslash (`java.io.File.path`) and forward-slash (`VirtualFile.path`) forms, so a subtree covered by another workspace folder was still expanded in place. Paths are now compared as normalized absolute keys, and a covered directory shows up as a single non-expandable folder node.

### 变更

- 面板图标、“文件夹”菜单图标与主工具栏小组件图标改用插件自带的一份 workspace 画法（折线取平台 sourceRoot 图标的文件夹边框色，浅色 `#3574F0`、深色 `#548AF7`）；小组件那份把文件夹内面置空（纯透明），面板与菜单保持有内面填充。插件 logo 未变；第三方图标的来源与许可现已登记在 `THIRD-PARTY-NOTICES.md`。
- 插件显示名由 `Multi-Root Workspace View` 改为 `Multi-Root Workspace`：一并调整英文设置页标题（工具 → Multi-Root Workspace）、市场描述与中英 README 里的同类措辞；插件 ID 未变，已安装用户可照常升级。
- 统一英文文案的句式大小写：设置页的复选框与“文件夹”菜单等条目改用句子式大写（如 “Hide the folder location”），与 IntelliJ 平台自身控件的写法一致；顺带修正一处提示文案引用了错误设置项名的问题。
- **破坏性变更**：插件 ID 由 `net.navifox.plugins.multi-root-workspace-view` 改为 `net.navifox.plugins.mr-workspace`。IDE 会把二者视为两个不同的插件，升级前请先卸载旧插件再安装新版本，旧插件也不会再收到更新；构建产物名随之改为 `mr-workspace-<version>.zip`。
- 统一内部标识符：面板类 `MrWorkspaceViewPane` 更名为 `MrWorkspacePane`，面板 ID 与项目级设置的持久化名改用更短的 `MrWorkspace` 前缀。这些标识写在项目的 `workspace.xml` 中，升级后“项目”工具窗口会回到原生“项目”面板（需重新选择“多根工作区”视图，其此前保存的展开状态不再保留），各项目在设置页选定的“多根工作区目录配置源”与两个显示选项（“自动隐藏当前工作区目录”“不显示文件夹所在路径”）同样恢复默认。

### Changed

- The pane icon, the "Folders" menu icon and the main-toolbar widget icon now use a workspace glyph shipped with the plugin (its polyline takes the folder border color of the platform's sourceRoot icon — `#3574F0` in light themes, `#548AF7` in dark ones); the widget copy leaves the folder face empty (fully transparent), while the pane and menu copies keep it filled. The plugin logo is unchanged, and third-party icon sources and licenses are now documented in `THIRD-PARTY-NOTICES.md`.
- The plugin display name changes from `Multi-Root Workspace View` to `Multi-Root Workspace`, along with the English settings page title (Tools -> Multi-Root Workspace), the marketplace description and the same wording in both READMEs. Its ID is unchanged, so existing installations upgrade as usual.
- Unify English copy to sentence case: checkboxes on the settings page and entries such as the "Folders" menu now follow the capitalization used by IntelliJ Platform controls themselves (for example, "Hide the folder location"); a message that referenced a setting by the wrong name is fixed as well.
- **Breaking change**: the plugin ID changes from `net.navifox.plugins.multi-root-workspace-view` to `net.navifox.plugins.mr-workspace`. IDEs treat the two as separate plugins, so uninstall the old plugin before installing this release, and the old plugin will no longer receive updates; the distribution file is renamed to `mr-workspace-<version>.zip` as well.
- Unify internal identifiers: the pane class `MrWorkspaceViewPane` is renamed to `MrWorkspacePane`, and the pane ID with the project-level settings state name now use the shorter `MrWorkspace` prefix. These identifiers are stored in the project's `workspace.xml`, so after upgrading the Project tool window falls back to the native Project pane (the "Workspace (Multi-Root)" view must be selected again and its previously saved expansion state is discarded), and each project's "Workspace source file" setting plus the two display options ("Hide the Current Workspace Directory Automatically", "Hide the Folder Location") also return to their defaults.

## [0.1.2] - 2026-09-29

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
- 新增设置页（工具 → 多根工作区）选择使用的配置文件；无配置时显示不落盘的“（无可用配置文件）”占位。
- 存在多个配置文件并自动选用一个时气泡提醒一次。
- 界面支持英文与简体中文（消息资源包）。

### Added

- Add a "Workspace (Multi-Root)" pane to the Project tool window that renders VS Code-style multi-root workspaces (`.code-workspace`, JSONC).
- Support multiple root directories in parallel with deduplication: every physical file appears only once, under its deepest owning folder.
- Show missing/unparseable folders as warning nodes and keep folders outside the project browsable (PSI fallback).
- Keep native Project-tree behavior: context menus, drag & drop, speed search, sorting options, and Alt+F1 "Select in" navigation.
- Add an empty-state overlay with a "Create Configuration File" button when no usable configuration exists; creation goes through a Save dialog and writes a localized `.code-workspace` template, then opens it in the editor.
- Add a Settings page (Tools -> Multi-Root Workspace) to choose the configuration file; when none exists it shows a non-persisted "（无可用配置文件）" placeholder.
- Notify once when multiple configuration files exist and one is auto-picked.
- Support English and Simplified Chinese UI (message bundles).
