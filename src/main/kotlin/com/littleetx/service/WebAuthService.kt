package com.littleetx.service

import com.littleetx.dao.*
import com.yubico.webauthn.*
import com.yubico.webauthn.data.*
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.exception.AssertionFailedException
import com.yubico.webauthn.exception.InvalidSignatureCountException
import com.yubico.webauthn.exception.RegistrationFailedException
import io.ktor.util.logging.*
import org.slf4j.Logger
import java.util.*
import kotlin.jvm.optionals.getOrElse
import kotlin.random.Random

typealias RegistrationCredential = PublicKeyCredential<
                AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs>

typealias AssertionCredential = PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>

interface WebAuthService {
    suspend fun startRegistration(email: String, username: String): PublicKeyCredentialCreationOptions
    suspend fun startNewCredential(user: UserInfo): PublicKeyCredentialCreationOptions
    suspend fun finishRegistration(request: PublicKeyCredentialCreationOptions, credential: RegistrationCredential): Optional<UserInfo>
    suspend fun startAuthentication(email: String): AssertionRequest

    /**
     * This method will test whether the credential is valid and update the signature count.
     * @return user info if success
     */
    suspend fun finishAuthentication(request: AssertionRequest, credential: AssertionCredential): Optional<UserInfo>
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

    override suspend fun startNewCredential(user: UserInfo): PublicKeyCredentialCreationOptions {
        return relyingParty.startRegistration(
            StartRegistrationOptions.builder()
                .user(user.toUserIdentity())
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .build())
                .build()
        )
    }

    override suspend fun finishRegistration(request: PublicKeyCredentialCreationOptions, credential: RegistrationCredential): Optional<UserInfo> {
        try {
            val result = relyingParty.finishRegistration(
                FinishRegistrationOptions.builder()
                    .request(request)
                    .response(credential)
                    .build()
            )
            // save result
            val user = userRepo.findUserByEmail(request.user.name).getOrElse {
                userRepo.createUser(request.user.displayName, request.user.id, request.user.name)
            }
            credentialRepo.addRegistration(
                RegisteredCredential.builder()
                    .credentialId(result.keyId.id) // Credential ID and public key for credential
                    .userHandle(request.user.id)
                    .publicKeyCose(result.publicKeyCose)
                    .signatureCount(result.signatureCount)
                    .build()
            )
            return Optional.of(user)
        } catch (e: RegistrationFailedException) {
            logger.error("Registration failed", e)
            return Optional.empty()
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

    override suspend fun finishAuthentication(request: AssertionRequest, credential: AssertionCredential): Optional<UserInfo> {
        try {
            val result = relyingParty.finishAssertion(
                FinishAssertionOptions.builder()
                    .request(AssertionRequest.builder()
                        .publicKeyCredentialRequestOptions(request.publicKeyCredentialRequestOptions)
                        .build())
                    .response(credential)
                    .build()
            )
            if (!result.isSuccess) return Optional.empty()
            credentialRepo.updateRegistration(
                result.username,
                result.credential.credentialId,
                result.signatureCount,
            )
            return userRepo.findUserByEmail(result.username)
        } catch (e: InvalidSignatureCountException) {
            logger.error("Invalid signature count", e)
        } catch (e: AssertionFailedException) {
            logger.error("Assertion failed", e)
        }
        return Optional.empty()
    }
}