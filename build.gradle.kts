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
        // webstorm("2025.3.6.1")
        // webstorm("2026.2.3")
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

tasks.register("verifyBuildPlatform") {
    group = "verification"
    description = "校验打包构建时产品与 `sinceBuild` 的主版本号是否一致。"

    val buildNumber = intellijPlatform.productInfo.buildNumber
    val sinceNumber = intellijPlatform.pluginConfiguration.ideaVersion.sinceBuild.get()
    val buildMajor = buildNumber.substringBefore(".")
    val sinceMajor = sinceNumber.substringBefore(".")

    doFirst {
        if (buildMajor != sinceMajor) {
            throw GradleException("打包构建时，平台版本号 $buildNumber 的主版本号必须与配置的 sinceBuild 的 $sinceMajor 相匹配。")
        }
    }
}

tasks.named("buildSearchableOptions") {
    // buildSearchableOptions 是构建任务最早的、独有的子任务。
    // 要是挂靠到其它非独有任务会影响其它主任务，比如运行、调试什么的。
    // 要是挂靠其它子任务可能会出现包都打完了才提示拒绝。
    dependsOn("verifyBuildPlatform")
}

tasks.named("buildPlugin") {
    // 不过 buildSearchableOptions 可以通过参数跳过，所以在整个主任务加一个依赖作为兜底。
    dependsOn("verifyBuildPlatform")
}
