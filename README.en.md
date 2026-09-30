# Multi-Root Workspace View

[中文](README.md) / English

A JetBrains IDE plugin that renders a VS Code-style workspace view inside the "Project" (Project) tool window.

![Screenshot](preview.webp)

## Highlights

- Shows multiple folders at once, and their paths may even nest inside one another — something the native **multi-workspace** feature does not allow.
- Reads VS Code `.code-workspace` configuration files directly, and lets you switch between them in "Settings".
- Feels and behaves like the native JetBrains IDE directory tree.
- Supports multiple languages.

## Compatibility

- Works with IntelliJ Platform products 2025.3 through 2026.2.\* (internal builds `253` to `262.*`).
- Multi-workspace is not on the roadmap: at any one time the view presents the folders of **one** `.code-workspace` configuration only.

## Getting started

1. Open a project in any JetBrains IDE you like.
2. Open the "Project" tool window and switch to **Multi-Root Workspace** in the drop-down at its top-left corner.
3. The folders configured in the `folders` field of the `.code-workspace` file in the project root are then shown right away.
4. If "No configuration available" appears, click the **Create Configuration File** button beneath the message and confirm in the save dialog — a new `.code-workspace` file is created in the project root.

## Structure

```
.
├── .run/                    Predefined run/debug configurations
├── gradle/
│   ├── wrapper/             Gradle wrapper
│   ├── libs.versions.toml   Version catalog
├── src/                     Plugin source code
│   └── main/
│       ├── java/            Source code (Java). Not used by this project
│       ├── kotlin/          Source code (Kotlin)
│       └── resources/       Plugin resources
│           ├── META-INF/    Plugin configuration and icons
│           └── messages/    Message bundles
├── .gitignore               Git ignore rules
├── build.gradle.kts         Gradle build configuration
├── gradle.properties        Gradle properties
├── gradlew                  Gradle wrapper script (*nix)
├── gradlew.bat              Gradle wrapper script (Windows)
├── README.md                This file
└── settings.gradle.kts      Gradle settings
```

```
./src/main/kotlin/net/navifox/plugins/
├─ NavifoxMessageBundle.kt               dynamic message bundle wrapper
├─ core/                                 *.code-workspace handling toolkit (no UI dependency)
│  ├─ Jsonc.kt                           JSONC → strict JSON sanitizer (comments/trailing commas/BOM)
│  ├─ MrWorkspace.kt                     data model and exception
│  ├─ MrWorkspaceParser.kt               parsing and path-resolution pure functions
│  └─ MrWorkspaceSelector.kt             discovery / selection / fallback loading
└─ workspace/                            feature UI layer
   ├─ MrWorkspacePane.kt                 main pane class (incl. empty-state overlay)
   ├─ MrWorkspaceSelectInTarget.kt       Alt+F1 select-in target
   ├─ MrWorkspaceConfigCreator.kt        "create configuration" flow (save dialog + template)
   ├─ MrWorkspaceSettings.kt             project-level settings
   └─ MrWorkspaceSettingsConfigurable.kt settings page
```

## Documentation

### Plugin development references

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)

## Links

- 罗狐会馆, QQ group [540457640](https://qm.qq.com/q/7WO1tJmTss).
