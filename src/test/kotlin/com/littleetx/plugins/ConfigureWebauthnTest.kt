package com.littleetx.plugins

import io.ktor.client.request.*
import io.ktor.server.testing.*
import kotlin.test.Test

class ConfigureWebauthnTest {

    @Test
    fun testPostRegisterBegin() = testApplication {
        application {
            configureRouting()
        }
        client.post("/register/begin").apply {
            TODO("Please write your test here")
        }
    }

    @Test
    fun testPostRegisterSubmit() = testApplication {
        application {
            configureRouting()
        }
        client.post("/register/submit").apply {
            TODO("Please write your test here")
        }
    }
}