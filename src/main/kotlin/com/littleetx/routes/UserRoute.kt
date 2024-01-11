package com.littleetx.routes

import com.littleetx.dao.CredentialInfo
import com.littleetx.dao.UserInfo
import com.littleetx.plugins.UserSession
import com.littleetx.service.UserService
import com.littleetx.service.UserServiceImpl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import kotlin.jvm.optionals.getOrNull


fun Route.userRoute() {
    val userService: UserService = UserServiceImpl
    val log = application.log

    route("/user") {
        @Serializable
        data class UserEntity(
            val id: Int,
            val username: String,
            val email: String,
            val userHandle: String,
        )
        fun UserInfo.toEntity() = UserEntity(id.value, username, email, userHandle)

        get {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Not login!")
            call.respond(user.toEntity())
        }

        @Serializable
        data class CredentialEntity(
            val credentialId: String,
            val publicKeyCose: String,
            val signatureCount: Long,
        )
        fun CredentialInfo.toEntity() = CredentialEntity(credentialId, publicKeyCose, signatureCount)

        get("/credentials") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown session")
            if (userService.getUserInfo(session).isEmpty) {
                return@get call.respond(HttpStatusCode.BadRequest, "Not login!")
            }
            val credentials = userService.getUserCredentials(session)
            call.respond(credentials.map { it.toEntity() })
        }
    }

    post("/logout") {
        val session = call.sessions.get<UserSession>()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")
        userService.logout(session)
        log.info("User logout: $session")
        call.respond(HttpStatusCode.OK)
    }
}