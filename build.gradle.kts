buildscript {
    repositories.mavenCentral()
    dependencies.classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.16.3")
}

plugins {
    kotlin("jvm") version "1.6.0" apply false
    // id("com.anatawa12.auto-tostring") version "1.0.2" apply false
}

group = "com.anatawa12.relocator"
version = property("version").toString()

subprojects {
    group = rootProject.group
    version = rootProject.version

    afterEvaluate {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }
    }
}
