# Multi-Root Workspace View

[中文](README.md) / English

A JetBrains IDE plugin that renders a VS Code-style workspace view inside the "Project" (Project) tool window.

- Shows multiple folders at once, and their paths may even nest inside one another — something the native **multi-workspace** feature does not allow.
- Reads VS Code `.code-workspace` configuration files directly, and lets you switch between them in "Settings".
- Feels and behaves like the native JetBrains IDE directory tree.
- Supports multiple languages.

![Screenshot](preview.webp)

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
│  ├─ MrWorkspace.kt                     data model and exception
│  ├─ MrWorkspaceParser.kt               parsing (kotlinx.serialization) and path-resolution pure functions
│  └─ MrWorkspaceSelector.kt             discovery / selection / fallback loading
└─ workspace/                            feature UI layer
   ├─ MrWorkspacePane.kt                 main pane class (incl. empty-state overlay)
   ├─ MrWorkspaceFoldersMenu.kt          "Folders" menu (Alt+F2, top-level folder filter)
   ├─ MrWorkspaceSelectInTarget.kt       Alt+F1 select-in target
   ├─ MrWorkspaceConfigCreator.kt        "create configuration" flow (save dialog + template)
   ├─ MrWorkspaceSettings.kt             project-level settings
   └─ MrWorkspaceSettingsConfigurable.kt settings page
```

## Workflow

Commands are shown in *nix form (Git Bash included); on Windows use `.\gradlew.bat` instead (for example `.\gradlew.bat runIde`).
The `.run/` directory also contains matching run configurations, so they can be launched straight from the IDE.

1. JDK 21 or newer is required.

### Run the IDE with the plugin

```shell
./gradlew runIde
```

### Build the plugin

```shell
./gradlew buildPlugin
```

1. The artifact lands in `./build/distributions/`.
2. The build also generates the searchable options (`buildSearchableOptions`); that stage requires the sandbox UI language to be English (`en`), otherwise the build fails.

### Run tests

```shell
./gradlew check
```

1. `check` is the aggregating verification task; it currently runs `test` first: most cases are plain JUnit tests (parsing and paths), and a smoke test boots the platform once to verify that the plugin class loader can reach the platform-provided library.

### Run verifications

```shell
./gradlew verifyPlugin
```

1. Runs the IntelliJ Plugin Verifier against the target platform; the verifier is downloaded on first use.

### Clean

```shell
./gradlew clean
```

1. Removes the build outputs under `./build/`; the sandbox and the Gradle caches are left untouched.

### Clean the sandbox

```shell
./gradlew cleanSandbox
```

1. Removes the sandbox under `./.intellijPlatform/sandbox/`; the UI language, opened projects, logs and indexes are all discarded.

### Sign and publish the plugin

```shell
./gradlew signPlugin
./gradlew publishPlugin
```

1. Both need credentials, supplied through environment variables: `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD` for signing, `PUBLISH_TOKEN` for publishing.
2. This project is currently built locally only; none of these credentials are configured yet.

## References

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)

## Links

- 罗狐会馆, QQ group [540457640](https://qm.qq.com/q/7WO1tJmTss).
