package com.littleetx.service

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import java.util.*


val credentialRepository = InMemoryRegistrationStorage


object InMemoryRegistrationStorage : CredentialRepository {
    private val cache = mutableMapOf<String, Set<RegisteredCredential>>()

    override fun getCredentialIdsForUsername(username: String): Set<PublicKeyCredentialDescriptor> {
        return cache[username]
            ?.map { PublicKeyCredentialDescriptor.builder().id(it.credentialId).build() }
            ?.toSet()
            ?: emptySet()
    }

    override fun getUserHandleForUsername(username: String): Optional<ByteArray> {
        return Optional.ofNullable(cache[username]?.firstOrNull()?.userHandle)
    }

    override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> {
        return Optional.ofNullable(cache.entries.firstOrNull { it.value.firstOrNull()?.userHandle == userHandle }?.key)
    }

    override fun lookup(credentialId: ByteArray, userHandle: ByteArray): Optional<RegisteredCredential> {
        return Optional.ofNullable(
            cache.values
                .flatten()
                .firstOrNull { it.credentialId == credentialId && it.userHandle == userHandle }
        )
    }

    override fun lookupAll(credentialId: ByteArray): Set<RegisteredCredential> {
        return cache.values
            .flatten()
            .filter { it.credentialId == credentialId }
            .toSet()
    }

    fun addRegistration(username: String, registration: RegisteredCredential) {
        cache[username] = cache[username]?.plus(registration) ?: setOf(registration)
    }
    fun updateRegistration(username: String, credentialId: ByteArray, signatureCount: Long) {
        cache[username] = cache[username]?.map {
            if (it.credentialId == credentialId) {
                RegisteredCredential.builder()
                    .credentialId(it.credentialId)
                    .userHandle(it.userHandle)
                    .publicKeyCose(it.publicKeyCose)
                    .signatureCount(signatureCount)
                    .build()
            } else {
                it
            }
        }?.toSet() ?: emptySet()
    }
}