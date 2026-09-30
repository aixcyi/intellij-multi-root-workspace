import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")  // 添加 Kotlin 支持。
    id("org.jetbrains.changelog")  // 简化对更新日志 CHANGELOG.md 的提取（应用到插件的更新日志上）。
    id("org.jetbrains.intellij.platform")  // IntelliJ 平台 Gradle 插件。
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ 平台 Gradle 插件依赖
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        webstorm("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // 平台内置的 kotlinx-serialization-json：用于解析 *.code-workspace（JSONC）的注释与尾逗号。
        // 用 bundledModule 而不是 bundledLibrary：官方明确后者不要用于生产。
        bundledModule("intellij.libraries.kotlinx.serialization.json")

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        description = file("src/main/resources/META-INF/pluginDescription.html").readText()

        // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginConfiguration-ideaVersion
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "262.*"
        }
    }
}
