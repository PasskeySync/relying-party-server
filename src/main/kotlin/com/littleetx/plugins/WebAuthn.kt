package com.littleetx.plugins

import com.littleetx.service.credentialRepository
import com.yubico.webauthn.*
import com.yubico.webauthn.data.*
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.exception.AssertionFailedException
import com.yubico.webauthn.exception.RegistrationFailedException
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.random.Random

val relyingPartyIdentity: RelyingPartyIdentity = RelyingPartyIdentity.builder()
    .id("localhost")
    .name("Example Application")
    .build()

val relyingParty: RelyingParty = RelyingParty.builder()
    .identity(relyingPartyIdentity)
    .credentialRepository(credentialRepository)
    .origins(setOf("http://localhost:3000"))
    .build()

val registryRequestCache = mutableMapOf<UUID, PublicKeyCredentialCreationOptions>()
val authnRequestCache = mutableMapOf<UUID, AssertionRequest>()
fun Application.configureWebauthn() {
    install(ContentNegotiation) {
         json()
    }
    val logger = LoggerFactory.getLogger("Webauthn")
    routing {
        @Serializable
        data class RegisterInfo(
            val username: String,
            val email: String,
        )
        post("/register/begin") {
            val info = call.receive<RegisterInfo>()
            if (credentialRepository.getCredentialIdsForUsername(info.email).isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "User already exists")
                return@post
            }
            val session = call.sessions.get<UserSession>() ?: UserSession()
            call.sessions.set(session)

            val userHandle = ByteArray(Random.Default.nextBytes(32))
            val credentialCreationOptions = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                    .user(
                        UserIdentity.builder()
                        .name(info.email)
                        .displayName(info.username)
                        .id(userHandle)
                        .build())
                    .authenticatorSelection(
                        AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .build())
                    .build()
            )
            registryRequestCache[session.uid] = credentialCreationOptions
            call.respondText(credentialCreationOptions.toCredentialsCreateJson())
        }

        post("register/finish") {
            val publicKeyCredentialJson = call.receiveText()
            logger.info("receiving publicKeyCredentialJson: $publicKeyCredentialJson")
            val pkc = PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson)

            val session = call.sessions.get<UserSession>() ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Unknown session")


            val request = registryRequestCache[session.uid] ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Unknown request")

            try {
                val result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                        .request(request)
                        .response(pkc)
                        .build()
                )
                // save result
                logger.info("User ${request.user.name} registered")
                registryRequestCache.remove(session.uid)
                credentialRepository.addRegistration(
                    request.user.name, // Username or other appropriate user identifier
                    RegisteredCredential.builder()
                        .credentialId(result.keyId.id) // Credential ID and public key for credential
                        .userHandle(request.user.id)
                        .publicKeyCose(result.publicKeyCose)
                        .signatureCount(result.signatureCount)
                        .build()
                )
                call.respond(HttpStatusCode.OK, "Login success")
            } catch (e: RegistrationFailedException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Registration failed")
            }
        }

        @Serializable
        data class LoginInfo(
            val email: String,
        )
        post("/login/begin") {
            val info = call.receive<LoginInfo>()
            if (credentialRepository.getCredentialIdsForUsername(info.email).isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "User does not exist")
                return@post
            }
            val session = call.sessions.get<UserSession>() ?: UserSession()
            call.sessions.set(session)
            val request = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                    .username(info.email) // Or .userHandle(ByteArray) if preferred
                    .build()
            )
            authnRequestCache[session.uid] = request
            call.respondText(request.toCredentialsGetJson())
        }

        post("login/finish") {
            val publicKeyCredentialJson = call.receiveText()
            val pkc = PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson)
            val session = call.sessions.get<UserSession>() ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Unknown session")
            val request = authnRequestCache[session.uid] ?: return@post call.respond(
                HttpStatusCode.BadRequest, "Unknown request")

            try {
                val result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                        .request(AssertionRequest.builder()
                            .publicKeyCredentialRequestOptions(request.publicKeyCredentialRequestOptions)
                            .build())
                        .response(pkc)
                        .build()
                )

                if (!result.isSuccess) {
                    call.respond(HttpStatusCode.BadRequest, "Assertion failed")
                    return@post
                }
                //TODO: 持久化用户数据
                credentialRepository.updateRegistration(              // Some database access method of your own design
                    request.username.get(),                  // Query by username or other appropriate user identifier
                    result.credential.credentialId,
                    result.signatureCount,
                )
                call.respond(HttpStatusCode.OK, "Login Success")
            } catch (e: AssertionFailedException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Assertion failed")
            }
        }
    }
}