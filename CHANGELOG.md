# Changelog 更新日志

This file is written in both English and Chinese, following the conventions of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件使用中英双语编写，遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 中的约定。

## [Unreleased]

## [0.1.0] - 2026-09-08

### Added

- 在“项目”工具窗口新增“多根工作区”面板，渲染 VS Code 风格的多根工作区（`.code-workspace`，JSONC）。
- 支持并列展示多个根目录并做内容根去重：同一物理文件只在所属最深的文件夹下出现一次。
- 缺失/无法解析的 folder 以警示节点展示；项目外目录仍可浏览（PSI 回退）。
- 保持与原生 Project 树一致的操作：右键菜单、拖拽、速度搜索、排序依据、Alt+F1“在视图中选择”定位。
- 无可用配置（无文件或全部解析失败）时显示空态覆盖层与“创建配置文件”按钮；创建走保存对话框并写入本地化模板后打开编辑。
- 新增设置页（工具 → 多根工作区视图）选择使用的配置文件；无配置时显示不落盘的“（无可用配置文件）”占位。
- 存在多个配置文件并自动选用一个时气泡提醒一次。
- 界面支持英文与简体中文（消息资源包）。


- Add a "Workspace (Multi-Root)" pane to the Project tool window that renders VS Code-style multi-root workspaces (`.code-workspace`, JSONC).
- Support multiple root directories in parallel with deduplication: every physical file appears only once, under its deepest owning folder.
- Show missing/unparseable folders as warning nodes and keep folders outside the project browsable (PSI fallback).
- Keep native Project-tree behavior: context menus, drag & drop, speed search, sorting options, and Alt+F1 "Select in" navigation.
- Add an empty-state overlay with a "Create Configuration File" button when no usable configuration exists; creation goes through a Save dialog and writes a localized `.code-workspace` template, then opens it in the editor.
- Add a Settings page (Tools -> Multi-Root Workspace View) to choose the configuration file; when none exists it shows a non-persisted "（无可用配置文件）" placeholder.
- Notify once when multiple configuration files exist and one is auto-picked.
- Support English and Simplified Chinese UI (message bundles).
