package com.littleetx.plugins

import com.littleetx.routes.authnRoute
import com.littleetx.routes.userRoute
import com.littleetx.routes.webauthnRoute
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*


fun Application.configureRouting() {
    install(ContentNegotiation) {
         json()
    }
    routing {
        webauthnRoute()
        authnRoute()
        userRoute()
    }
}