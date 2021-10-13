plugins {
    kotlin("jvm") version "1.5.10" apply false
}

group = "com.anatawa12.relocator"
version = property("version").toString()

subprojects {
    group = rootProject.group
    version = rootProject.version
}
