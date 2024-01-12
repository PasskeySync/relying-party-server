package com.littleetx.routes

import com.littleetx.dao.userRepo
import com.littleetx.plugins.UserSession
import com.littleetx.service.userService
import com.littleetx.service.webAuthService
import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.jvm.optionals.getOrNull

private val registryRequestCache = mutableMapOf<UUID, PublicKeyCredentialCreationOptions>()
private val authnRequestCache = mutableMapOf<UUID, AssertionRequest>()
fun Route.webauthnRoute() {
    val log = application.log

    @Serializable
    data class RegisterInfo(
        val username: String,
        val email: String,
    )

    fun RegisterInfo.isValid(): Boolean {
        return username.isValidUsername() && email.isValidEmail()
    }

    route("/register") {
        post("/begin") {
            val info = call.receive<RegisterInfo>()
            if (!info.isValid()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid username or email")
            }
            if (userRepo.findUserByEmail(info.email).isPresent) {
                return@post call.respond(HttpStatusCode.BadRequest, "User already exists")
            }
            val session = call.sessions.getOrSet<UserSession> { UserSession() }
            val options = webAuthService.startRegistration(info.email, info.username)
            registryRequestCache[session.sessionID] = options
            call.respondText(options.toCredentialsCreateJson())
        }

        post("/create") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Not login!")

            val options = webAuthService.startNewCredential(user)
            registryRequestCache[session.sessionID] = options
            call.respondText(options.toCredentialsCreateJson())
        }

        post("/finish") {
            val publicKeyCredentialJson = call.receiveText()
            log.info("receiving publicKeyCredentialJson: $publicKeyCredentialJson")
            val pkc = PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson)

            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")

            val request = registryRequestCache[session.sessionID]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown request")
            val user = webAuthService.finishRegistration(request, pkc).getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Registration failed")

            log.info("User ${request.user.name} registered")
            registryRequestCache.remove(session.sessionID)
            userService.login(session, user)
            call.respond(HttpStatusCode.OK, "Registration Success")
        }
    }

    route("/login") {
        @Serializable
        data class LoginInfo(
            val email: String,
        )
        fun LoginInfo.isValid(): Boolean {
            return email.isValidEmail()
        }

        post("/begin") {
            val info = call.receive<LoginInfo>()
            if (info.email.isNotEmpty() && !info.isValid()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid email")
            }
            if (info.email.isNotEmpty() && userRepo.findUserByEmail(info.email).isEmpty) {
                return@post call.respond(HttpStatusCode.BadRequest, "User does not exist")
            }
            val session = call.sessions.getOrSet<UserSession> { UserSession() }

            val request = webAuthService.startAuthentication(info.email)
            authnRequestCache[session.sessionID] = request
            call.respondText(request.toCredentialsGetJson())
        }

        post("/finish") {
            val publicKeyCredentialJson = call.receiveText()
            val pkc = PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson)
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val request = authnRequestCache[session.sessionID]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown request")
            val user = webAuthService.finishAuthentication(request, pkc).getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Authentication failed")

            log.info("User ${user.email} authenticated")
            userService.login(session, user)
            call.respond(HttpStatusCode.OK, "Authentication Success")
        }
    }
}