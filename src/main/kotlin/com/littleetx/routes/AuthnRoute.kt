package com.littleetx.routes

import com.littleetx.dao.passwordRepo
import com.littleetx.dao.userRepo
import com.littleetx.plugins.UserSession
import com.littleetx.service.userService
import com.yubico.webauthn.data.ByteArray
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlin.jvm.optionals.getOrNull
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
fun Route.authnRoute() {
    val log = application.log

    post("/logout") {
        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")
        userService.logout(session)
        log.info("User logout: $session")
        call.respond(HttpStatusCode.OK)
    }

    @Serializable
    data class RegisterInfo(
        val username: String,
        val email: String,
        val password: String,
    )
    fun RegisterInfo.isValid(): Boolean {
        return username.isValidUsername() && email.isValidEmail() && password.isValidPassword()
    }

    post("register/plain") {
        val info = call.receive<RegisterInfo>()
        if (!info.isValid()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid register info")
        }
        val session = call.sessions.get<UserSession>() ?: UserSession()
        call.sessions.set(session)
        if (userRepo.findUserByEmail(info.email).isPresent) {
            return@post call.respond(HttpStatusCode.BadRequest, "User already exists")
        }

        val userHandle = ByteArray(Random.nextBytes(32))
        val user = userRepo.createUser(info.username, userHandle, info.email)
        passwordRepo.setPassword(user, encodePassword(info.password))
        passwordRepo.setEnablePassword(user, true)
        userService.login(session, user)
        log.info("User ${user.email} register via plain password")
        return@post call.respond(HttpStatusCode.OK)
    }


    @Serializable
    data class LoginInfo(
        val email: String,
        val password: String,
    )
    fun LoginInfo.isValid(): Boolean {
        return email.isValidEmail() && password.isValidPassword()
    }
    post("login/plain") {
        val info = call.receive<LoginInfo>()
        if (!info.isValid()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Invalid email or password")
        }
        val session = call.sessions.get<UserSession>() ?: UserSession()
        call.sessions.set(session)

        val user = userRepo.findUserByEmail(info.email).getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "User not exists")
        val passwordInfo = passwordRepo.getPasswordInfo(user)
        if (!passwordInfo.usePassword) {
            return@post call.respond(HttpStatusCode.BadRequest, "User disabled password login")
        }
        if (passwordInfo.password != encodePassword(info.password)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Incorrect password")
        }
        userService.login(session, user)
        log.info("User ${user.email} login via plain password")
        return@post call.respond(HttpStatusCode.OK)
    }
}