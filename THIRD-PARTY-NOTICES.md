# 第三方组件与许可声明

本项目自身**尚未选择许可证**（保留所有权利）；本文件仅登记随本项目分发的第三方资源及其许可要求，
不构成对项目自身代码的授权。

## 1. IntelliJ Platform 图标（Apache-2.0）

| 文件 | 来源 | 修改 |
| --- | --- | --- |
| `src/main/resources/icons/workspace.svg` | IntelliJ Platform `expui/nodes/workspace.svg` | 折线改成 `#3574F0` |
| `src/main/resources/icons/workspace_dark.svg` | IntelliJ Platform `expui/nodes/workspace_dark.svg` | 折线改成 `#548AF7` |
| `src/main/resources/icons/workspaceWidget.svg` | 同上（浅色） | 折线改成 `#3574F0`，并把文件夹内面置空（纯透明） |
| `src/main/resources/icons/workspaceWidget_dark.svg` | 同上（深色） | 折线改成 `#548AF7`，并把文件夹内面置空（纯透明） |

- 来源仓库：<https://github.com/JetBrains/intellij-community>（`platform/` 下的 expui `nodes` 图标），
  并与 IntelliJ 平台发行包中的同名资源逐一核对。
- 许可：**Apache License 2.0**；每个 SVG 文件头部均保留原始版权与许可声明。
- 修改说明（对应 Apache-2.0 第 4 条 (b)）：折线（第 2 条 `path`）的颜色改为平台 sourceRoot 图标里
  文件夹的边框色——浅色 `#3574F0`、深色 `#548AF7`；`workspaceWidget*.svg` 额外把文件夹内面
  （第 3 条 `path`）的填充设为 `none`，使内部纯透明。除此之外图形与其余颜色均未改动，
  上述改动也写在每个文件头部的注释里。
- 许可证副本：`src/main/resources/META-INF/LICENSE-APACHE-2.0.txt`（随插件打包分发）。
- 上游 NOTICE：`src/main/resources/META-INF/NOTICE.txt`（对应 Apache-2.0 第 4 条 (d)），原文为
  “This software includes code from IntelliJ IDEA / Copyright (C) JetBrains s.r.o. / https://www.jetbrains.com/idea/”。

## 2. 插件 logo（catppuccin/vscode-icons）

| 文件 | 来源 | 修改 |
| --- | --- | --- |
| `src/main/resources/META-INF/pluginIcon.svg` | <https://github.com/catppuccin/vscode-icons> | 无（原样使用） |
| `src/main/resources/META-INF/pluginIcon_dark.svg` | <https://github.com/catppuccin/vscode-icons> | 无（原样使用） |

- 许可：见 <https://github.com/catppuccin/vscode-icons/blob/main/LICENSE>（两个文件头部已保留来源与许可链接）。
- 说明：平台自带图标（如 workspace 图标）**不用于**插件 logo——JetBrains Marketplace 审批指南要求
  插件 logo 不得与 JetBrains 产品标识相似，故 logo 沿用第三方画法。

## 3. 其它说明

- 上述第三方图标仅用于插件自身的界面与展示，不代表 JetBrains 或 catppuccin 对本项目的认可、赞助或背书。
- 如需在再分发时移除这些图标，删除 `src/main/resources/icons/` 与 `META-INF/pluginIcon*.svg`
  并同步调整 `MrWorkspaceIcons` 即可，项目自身代码不受影响。
