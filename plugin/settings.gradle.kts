rootProject.name = "curlconverter-intellij"

pluginManagement {
    repositories {
        // Maven Central mirror first: repo.maven.apache.org rate-limits (429) from shared IPs.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
    }
}
