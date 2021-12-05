import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

apply<kotlinx.atomicfu.plugin.gradle.AtomicFUGradlePlugin>()

group = "com.anatawa12.relocator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.ow2.asm:asm:9.2")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    testImplementation(kotlin("reflect"))
    testImplementation(platform("org.junit:junit-bom:5.8.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.amshove.kluent:kluent:1.68")
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
