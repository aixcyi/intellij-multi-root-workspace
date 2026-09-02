# Multi Root Workspace View

一个 JetBrains IDE 插件，提供了对多根工作区（Multi-root Workspace）在目录树中的支持。

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
