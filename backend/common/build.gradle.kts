plugins {
    kotlin("jvm") version "2.3.0"
}

group = "com.zyntasolutions.zyntapos"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.1"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
}
