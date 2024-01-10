package com.littleetx

import com.littleetx.plugins.configureDatabase
import com.littleetx.plugins.configureSecurity
import com.littleetx.plugins.configureWebauthn
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    configureDatabase()
    configureSecurity()
    configureWebauthn()
}
