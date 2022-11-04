plugins {
    val kotlinVersion = "1.7.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion


    id("net.mamoe.mirai-console") version "2.11.1"
}

group = "Linmeng"
version = "1.2.0"

repositories {
    maven ("https://maven.aliyun.com/repository/public")
    mavenCentral()
}
dependencies {
    // ktor core
    implementation("io.ktor:ktor-client-core:2.1.1")
    implementation("io.ktor:ktor-client-okhttp:2.1.1")
    implementation("io.ktor:ktor-client-websockets:2.1.1")
    // ktor serialization
    implementation("io.ktor:ktor-client-content-negotiation:2.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.1")
}
