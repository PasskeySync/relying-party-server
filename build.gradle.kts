val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val webauthn_server_version: String by project
val webauthn4j_version: String by project
val ehcache_version: String by project

plugins {
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.serialization") version "1.9.21"
    id("io.ktor.plugin") version "2.3.6"
}

group = "com.littleetx"
version = "0.0.1"

application {
    mainClass.set("com.littleetx.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

    implementation("org.ehcache:ehcache:$ehcache_version") // caching

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("com.yubico:webauthn-server-core:$webauthn_server_version")
    implementation("org.projectlombok:lombok:1.18.30")
    implementation("com.webauthn4j:webauthn4j-core:$webauthn4j_version")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
