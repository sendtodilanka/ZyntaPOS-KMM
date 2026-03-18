plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

group = "com.zyntasolutions.zyntapos"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.1"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
}
