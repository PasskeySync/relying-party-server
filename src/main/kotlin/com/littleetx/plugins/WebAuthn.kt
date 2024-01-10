package com.littleetx.plugins

import com.littleetx.dao.UserRepo
import com.littleetx.dao.UserRepoImpl
import com.littleetx.service.WebAuthService
import com.littleetx.service.WebAuthServiceImpl
import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.util.*

private val registryRequestCache = mutableMapOf<UUID, PublicKeyCredentialCreationOptions>()
private val authnRequestCache = mutableMapOf<UUID, AssertionRequest>()
private val service: WebAuthService = WebAuthServiceImpl

fun Application.configureWebauthn() {
    install(ContentNegotiation) {
         json()
    }
    routing {
        val log = this@configureWebauthn.log
        val userRepo: UserRepo = UserRepoImpl
        @Serializable
        data class RegisterInfo(
            val username: String,
            val email: String,
        )
        post("/register/begin") {
            val info = call.receive<RegisterInfo>()
            if (userRepo.findUserByEmail(info.email).isPresent)
                return@post call.respond(HttpStatusCode.BadRequest, "User already exists")

            val session = call.sessions.get<UserSession>() ?: UserSession()
            call.sessions.set(session)
            val options = service.startRegistration(info.email, info.username)
            registryRequestCache[session.sessionID] = options
            call.respondText(options.toCredentialsCreateJson())
        }

        post("register/finish") {
            val publicKeyCredentialJson = call.receiveText()
            log.info("receiving publicKeyCredentialJson: $publicKeyCredentialJson")
            val pkc = PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson)

            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")

            val request = registryRequestCache[session.sessionID]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown request")

            if (service.finishRegistration(request, pkc)) {
                log.info("User ${request.user.name} registered")
                registryRequestCache.remove(session.sessionID)
                call.respond(HttpStatusCode.OK, "Registration Success")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Registration failed")
            }

        }

        @Serializable
        data class LoginInfo(
            val email: String,
        )
        post("/login/begin") {
            val info = call.receive<LoginInfo>()
            if (info.email.isNotEmpty() && userRepo.findUserByEmail(info.email).isEmpty) {
                return@post call.respond(HttpStatusCode.BadRequest, "User does not exist")
            }
            val session = call.sessions.get<UserSession>() ?: UserSession()
            call.sessions.set(session)

            val request = service.startAuthentication(info.email)
            authnRequestCache[session.sessionID] = request
            call.respondText(request.toCredentialsGetJson())
        }

        post("login/finish") {
            val publicKeyCredentialJson = call.receiveText()
            val pkc = PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson)
            val session = call.sessions.get<UserSession>() ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Unknown session")
            val request = authnRequestCache[session.sessionID] ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Unknown request")

            if (service.finishAuthentication(request, pkc)) {
                call.respond(HttpStatusCode.OK, "Authentication Success")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Authentication failed")
            }
        }
    }
}