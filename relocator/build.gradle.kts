plugins {
    kotlin("jvm")
}

group = "com.anatawa12.relocator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.ow2.asm:asm:9.2")
}
