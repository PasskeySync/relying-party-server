package com.littleetx.service

import com.littleetx.dao.CredentialRepo
import com.littleetx.dao.CredentialRepoImpl
import com.littleetx.dao.UserRepo
import com.littleetx.dao.UserRepoImpl
import com.yubico.webauthn.*
import com.yubico.webauthn.data.*
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.exception.AssertionFailedException
import com.yubico.webauthn.exception.RegistrationFailedException
import io.ktor.util.logging.*
import org.slf4j.Logger
import kotlin.random.Random

typealias RegistrationCredential = PublicKeyCredential<
                AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs>

typealias AssertionCredential = PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>

interface WebAuthService {
    suspend fun startRegistration(email: String, username: String): PublicKeyCredentialCreationOptions
    suspend fun finishRegistration(request: PublicKeyCredentialCreationOptions, credential: RegistrationCredential): Boolean
    suspend fun startAuthentication(email: String): AssertionRequest
    suspend fun finishAuthentication(request: AssertionRequest, credential: AssertionCredential): Boolean
}


val relyingPartyIdentity: RelyingPartyIdentity = RelyingPartyIdentity.builder()
    .id("localhost")
    .name("Example Application")
    .build()

val relyingParty: RelyingParty = RelyingParty.builder()
    .identity(relyingPartyIdentity)
    .credentialRepository(CredentialRepoImpl)
    .origins(setOf("http://localhost:3000")) // allow to authenticate dev frontend
    .build()

object WebAuthServiceImpl : WebAuthService {
    private val logger: Logger = KtorSimpleLogger(javaClass.name)
    private val credentialRepo: CredentialRepo = CredentialRepoImpl
    private val userRepo: UserRepo = UserRepoImpl
    override suspend fun startRegistration(email: String, username: String): PublicKeyCredentialCreationOptions {
        val userHandle = ByteArray(Random.nextBytes(32))
        return relyingParty.startRegistration(
            StartRegistrationOptions.builder()
                .user(
                    UserIdentity.builder()
                    .name(email)
                    .displayName(username)
                    .id(userHandle)
                    .build())
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                    .residentKey(ResidentKeyRequirement.REQUIRED)
                    .build())
                .build()
        )
    }

    override suspend fun finishRegistration(request: PublicKeyCredentialCreationOptions, credential: RegistrationCredential): Boolean {
        try {
            val result = relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                    .request(request)
                    .response(credential)
                    .build()
            )
            // save result
            val user = userRepo.createUser(request.user.displayName, request.user.id, request.user.name)
            credentialRepo.addRegistration(
                user,
                RegisteredCredential.builder()
                    .credentialId(result.keyId.id) // Credential ID and public key for credential
                    .userHandle(request.user.id)
                    .publicKeyCose(result.publicKeyCose)
                    .signatureCount(result.signatureCount)
                    .build()
            )
            return true
        } catch (e: RegistrationFailedException) {
            logger.error("Registration failed", e)
            return false
        }
    }

    override suspend fun startAuthentication(email: String): AssertionRequest {
        val options = if (email.isEmpty()) {
                StartAssertionOptions.builder()
                    .build()
            } else {
                StartAssertionOptions.builder()
                    .username(email) // Or .userHandle(ByteArray) if preferred
                    .build()
            }
        return relyingParty.startAssertion(options)
    }

    override suspend fun finishAuthentication(request: AssertionRequest, credential: AssertionCredential): Boolean {
        try {
            val result = relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                    .request(AssertionRequest.builder()
                        .publicKeyCredentialRequestOptions(request.publicKeyCredentialRequestOptions)
                        .build())
                    .response(credential)
                    .build()
            )
            if (!result.isSuccess) return false
            credentialRepo.updateRegistration(              // Some database access method of your own design
                result.username,                  // Query by username or other appropriate user identifier
                result.credential.credentialId,
                result.signatureCount,
            )
            return true
        } catch (e: AssertionFailedException) {
            logger.error("Assertion failed", e)
            return false
        }
    }
}