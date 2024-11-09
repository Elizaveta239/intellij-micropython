import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/maven-central")
    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
        snapshots()
    }
}

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

dependencies {

    intellijPlatform {
        val type = project.property("platformType").toString()
        val version = project.property("platformVersion").toString()
        val pythonPlugin = project.property("pythonPlugin").toString()

        create(type, version, useInstaller = false)

        bundledPlugin("org.jetbrains.plugins.terminal")

        when (type) {
            "PC" -> bundledPlugin("PythonCore")
            "PY" -> bundledPlugin("Pythonid")
            else -> plugin(pythonPlugin)
        }
    }

    implementation("org.java-websocket:Java-WebSocket:1.5.5")
    implementation("io.github.java-native:jssc:2.9.6") {
        exclude("org.slf4j", "slf4j-api")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        name = "MicroPython"
    }

    instrumentCode = false

    publishing {
        token = project.property("publishToken").toString()
    }
}

tasks {
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            languageVersion = KotlinVersion.DEFAULT
            apiVersion = KotlinVersion.KOTLIN_2_0
        }
    }
    prepareSandbox {
        from("$rootDir") {
            into("intellij-micropython")
            include("typehints/")
            include("scripts/")
        }
    }

    test {
        testLogging.showExceptions = true
        useJUnitPlatform()
    }
}