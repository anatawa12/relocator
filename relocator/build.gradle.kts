import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

apply<kotlinx.atomicfu.plugin.gradle.AtomicFUGradlePlugin>()

group = "com.anatawa12.relocator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    ksp(project(":builder-builder"))
    compileOnly(project(":builder-builder-lib"))

    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))
    implementation(kotlin("annotations-jvm"))
    implementation("org.ow2.asm:asm:9.2")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.1")

    testImplementation(kotlin("reflect"))
    testImplementation("org.ow2.asm:asm-tree:9.2")
    testImplementation(platform("io.kotest:kotest-bom:5.0.3"))
    testImplementation("io.kotest:kotest-framework-api")
    testImplementation("io.kotest:kotest-assertions-core")
    testRuntimeOnly(platform("io.kotest:kotest-bom:5.0.3"))
    testRuntimeOnly("io.kotest:kotest-runner-junit5")
}

kotlin.sourceSets.main {
    kotlin.srcDir("build/generated/ksp/main/kotlin")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}
