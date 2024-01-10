package com.littleetx

import com.littleetx.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    configureSecurity()
    configureRouting()
    configureWebauthn()
}
