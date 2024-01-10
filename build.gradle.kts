val ktorVersion: String by project
val kotlinVersion: String by project
val logbackVersion: String by project
val webauthnServerVersion: String by project
val ehcacheVersion: String by project
val exposedVersion: String by project
val h2Version: String by project


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
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("org.ehcache:ehcache:$ehcacheVersion") // caching

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion") // database
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.h2database:h2:$h2Version")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.yubico:webauthn-server-core:$webauthnServerVersion")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
