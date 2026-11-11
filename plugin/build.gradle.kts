import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    intellijPlatform { defaultRepositories() }
    // Maven Central via Google's mirror: repo.maven.apache.org answers 429 to shared CI IPs.
    maven("https://maven-central.storage-download.googleapis.com/maven2")
}

dependencies {
    intellijPlatform {
        // -PlocalIde=/path/to/ide compiles and tests against an installed IDE instead of a downloaded one.
        val localIde = providers.gradleProperty("localIde")
        if (localIde.isPresent) local(localIde)
        else create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        // 2026.2+: JCEF is the bundled plugin com.intellij.modules.jcef (see plugin.xml).
        if (localIde.isPresent && file("${localIde.get()}/plugins/jcef-plugin").isDirectory) bundledPlugin("com.intellij.modules.jcef")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        // Newer IDEs are built with newer Kotlin; only matters when compiling against -PlocalIde.
        if (providers.gradleProperty("localIde").isPresent) freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            // -PverifyIde=/path/to/ide checks the shipped (2024.2-compiled) zip against another IDE.
            val verifyIde = providers.gradleProperty("verifyIde")
            if (verifyIde.isPresent) local(file(verifyIde.get()))
            else create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        }
    }
    buildSearchableOptions = false
    instrumentCode = false
}

tasks.test {
    // Let the IDE test runtime start real JCEF (off-screen) so the JCEF engine is actually exercised.
    systemProperty("ide.browser.jcef.testMode.enabled", "true")
    systemProperty("ide.browser.jcef.headless.enabled", "true")
    // CI containers run as root, where Chromium's sandbox refuses to start. Desktop IDEs keep it on.
    systemProperty("ide.browser.jcef.sandbox.enable", "false")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
