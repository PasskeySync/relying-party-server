package com.littleetx.routes

import com.littleetx.dao.*
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


fun Route.userRoute() {

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

        delete {
            val session = call.sessions.get<UserSession>()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Not login!")
            userRepo.deleteUser(user)
            call.respond(HttpStatusCode.OK)
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

        delete("/credentials") {
            val session = call.sessions.get<UserSession>()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Not login!")
            val credentialId = call.parameters["credentialId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing credentialId")

            if (credentialRepo.deleteRegistration(user, ByteArray.fromBase64(credentialId))) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Failed to delete credential")
            }
        }

        get("/enable_password") {
            val session = call.sessions.get<UserSession>()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Not login!")
            call.respond(passwordRepo.getPasswordInfo(user).usePassword)
        }

        @Serializable
        data class EnablePasswordRequest(
            val enable: Boolean,
            val password: String,
        )
        fun EnablePasswordRequest.isValid(): Boolean {
            return password.isValidPassword()
        }
        post("/enable_password") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Not login!")
            val request = call.receive<EnablePasswordRequest>()
            if (!request.isValid()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid password")
            }
            val passwordInfo = passwordRepo.getPasswordInfo(user)
            if (request.enable == passwordInfo.usePassword) {
                return@post call.respond(HttpStatusCode.BadRequest, "Password already ${if (request.enable) "enabled" else "disabled"}")
            }
            if (request.enable) {
                passwordRepo.setPassword(user, encodePassword(request.password))
                passwordRepo.setEnablePassword(user, true)
            } else {
                if (passwordInfo.password != encodePassword(request.password)) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Password not match")
                }
                if (userService.getUserCredentials(session).isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "User has no credentials, can not disable password login")
                }
                passwordRepo.setEnablePassword(user, false)
            }
            call.respond(HttpStatusCode.OK)
        }

        @Serializable
        data class ResetPasswordRequest(
            val oldPassword: String,
            val newPassword: String,
        )
        fun ResetPasswordRequest.isValid(): Boolean {
            return oldPassword.isValidPassword() && newPassword.isValidPassword()
        }

        post("/reset_password") {
            val session = call.sessions.get<UserSession>()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unknown session")
            val user = userService.getUserInfo(session).getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Not login!")

            val request = call.receive<ResetPasswordRequest>()
            if (!request.isValid()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid password")
            }
            val passwordInfo = passwordRepo.getPasswordInfo(user)
            if (passwordInfo.password != encodePassword(request.oldPassword)) {
                return@post call.respond(HttpStatusCode.BadRequest, "Password not match")
            }
            passwordRepo.setPassword(user, encodePassword(request.newPassword))
            call.respond(HttpStatusCode.OK)
        }
    }
}