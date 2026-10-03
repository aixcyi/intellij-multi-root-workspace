# Multi-Root Workspace

中文／[English](README.en.md)

一个 JetBrains IDE 插件，可以在“项目”工具窗口（Project，Alt+F1）中呈现像 VS Code 那样的工作区。

- 同时展示多个文件夹，允许路径相互包含（原生的 **多工作区** 功能不允许这样子）。
- 允许切换某个文件夹。
- 快捷键 `Alt + F2` 快速切换。
- 直接依赖 VS Code 配置文件 `*.code-workspace` 并支持在“设置”中切换用哪个。
- 拥有与 JetBrains IDE 原生目录树一致的体验。
- 主工具栏（New UI）左侧提供“文件夹”小组件。
- 支持多种语言文字。

![示意图](preview.webp)

## 兼容

- 兼容版本 2025.3 到 2026.2.* 的 IntelliJ 平台产品，对应内部构建 `253` 至 `262.*`。
- 暂不考虑多工作区，目前只能呈现 **单个** 工作区配置 `.code-workspace` 的文件夹们。

## 开始

1. 挑一个顺手的 JetBrains IDE 打开项目。
2. 点开“项目”工具窗口，在左上角的下拉框中切换到 **多根工作区** 选项。
3. 此时会直接展示项目根目录下 `.code-workspace` 文件在 `folders` 字段配置的文件夹。
4. 如果显示“没有可用配置”，点击提示下方的按钮，在弹出的文件保存窗口中点击确定，即可在项目根目录下创建一个 `.code-workspace` 文件。

## 结构

```
.
├── .run/                   预定义的运行/调试配置
├── gradle/
│   ├── wrapper/            Gradle 包装器
│   ├── libs.versions.toml  版本目录
├── src/                    插件源代码
│   └── main/
│       ├── java/           源代码，Java 编写。本项目不使用 Java 所以目前没有
│       ├── kotlin/         源代码，Kotlin 编写
│       └── resources/      插件资源文件
│           ├── META-INF/   插件配置文件和图标
│           └── messages/   文本资源包
├── .gitignore              Git 忽略规则
├── build.gradle.kts        Gradle 构建配置
├── gradle.properties       Gradle 配置属性
├── gradlew                 *nix 系统下的 Gradle 包装器脚本
├── gradlew.bat             Windows 系统下的 Gradle 包装器脚本
├── README.md               本文件
└── settings.gradle.kts     Gradle 项目设置
```

```
./src/main/kotlin/net/navifox/plugins/
├─ NavifoxMessageBundle.kt               动态消息包封装
├─ core/                                 *.code-workspace 处理工具包（无 UI 依赖）
│  ├─ MrWorkspace.kt                     数据模型与异常
│  ├─ MrWorkspaceParser.kt               解析（kotlinx-serialization）与路径解析纯函数
│  └─ MrWorkspaceSelector.kt             文件发现/选定/顺延加载
└─ workspace/                            特性 UI 层
   ├─ MrWorkspacePane.kt                 面板主类（含空态覆盖层）
   ├─ MrWorkspaceFolderModel.kt          顶层文件夹的唯一真相（加载/去重/隐藏/展示信息）
   ├─ MrWorkspaceFoldersMenu.kt          “文件夹”菜单（Alt＋F2，顶层文件夹过滤）
   ├─ MrWorkspaceFoldersWidgetAction.kt  主工具栏（New UI）“文件夹”小组件
   ├─ MrWorkspaceSelectInTarget.kt       Alt+F1 定位目标
   ├─ MrWorkspaceConfigCreator.kt        新建配置流程（保存对话框 + 模板）
   ├─ MrWorkspaceSettings.kt             项目级设置
   └─ MrWorkspaceSettingsConfigurable.kt 设置页
```

## 工作流

1. 至少需要 JDK 21 或以上的版本。
2. Windows 命令行用户请改用 `./gradlew.bat`，例如 `./gradlew.bat runIde`。
3. IDEA 用户可以直接使用 `.run/` 下的运行配置来执行各个工作流。

### 带插件运行IDE

```shell
./gradlew runIde
```

1. 默认使用 `./build.gradle.kts` 中 `dependencies.intellijPlatform` 块的 `webstorm("2025.3.5")` 这个IDE来运行。

### 构建插件

```shell
./gradlew buildPlugin
```

1. 构建产物位于 `./build/distributions/` 。
2. 构建过程包含了可搜索选项的构建（`buildSearchableOptions`），这一阶段要求沙盒界面语言为英文（`en`），否则会导致构建失败。

### 运行测试

```shell
./gradlew check
```

1. `check` 是聚合验证任务，目前会先执行 `test`：大部分是纯 JUnit 用例（解析与路径）；另有冒烟用例会启动一次平台，验证插件类加载器能取到平台内置库。

### 运行校验

```shell
./gradlew verifyPlugin
```

1. 用 IntelliJ Plugin Verifier 针对目标平台做兼容性校验，首次执行需要下载校验工具。

### 清理

```shell
./gradlew clean
```

1. 清掉 `./build/` 下的构建产物，沙盒与 Gradle 缓存不受影响。

### 清理沙盒

```shell
./gradlew cleanSandbox
```

1. 清掉 `./.intellijPlatform/sandbox/` 下的沙盒，界面语言、已打开的项目、日志与索引都会一并消失。

### 插件签名与发布

```shell
./gradlew signPlugin
./gradlew publishPlugin
```

1. 两者都需要凭据，通过环境变量提供：签名用 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`，发布用 `PUBLISH_TOKEN`。

## 参考

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)

## 链接

- 罗狐会馆，QQ群 [540457640](https://qm.qq.com/q/7WO1tJmTss)。
