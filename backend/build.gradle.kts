import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

val versionFile = listOf(rootProject.file("version.properties"), rootProject.file("../version.properties"))
    .firstOrNull { it.isFile } ?: error("version.properties is required from the repository root.")
group = "com.thinkingweather"
version = Properties().apply { versionFile.inputStream().use { load(it) } }.getProperty("VERSION_NAME")

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.thinkingweather.gateway.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-modules=jdk.httpserver", "-Xms64m", "-Xmx256m",
        "-Djdk.httpserver.maxConnections=128", "-Dsun.net.httpserver.maxIdleConnections=16",
        "-Dsun.net.httpserver.maxReqHeaders=40", "-Dsun.net.httpserver.maxReqHeaderSize=16384",
        "-Dsun.net.httpserver.maxReqTime=10", "-Dsun.net.httpserver.maxRspTime=30",
        "-Djdk.http.maxHeaderSize=32768",
    )
}

tasks.test {
    jvmArgs("--add-modules=jdk.httpserver")
}
