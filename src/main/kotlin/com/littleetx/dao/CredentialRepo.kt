package com.littleetx.dao

import com.littleetx.plugins.query
import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.and
import java.util.*
import kotlin.jvm.optionals.getOrNull

object CredentialInfos : IntIdTable() {
    val credentialId = varchar("credential_id", 200)
    val user = reference("user", UserInfos)
    val publicKeyCose = varchar("public_key_cose", 200)
    val signatureCount = long("signature_count")
}

class CredentialInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CredentialInfo>(CredentialInfos)

    var user by UserInfo referencedOn CredentialInfos.user
    var credentialId by CredentialInfos.credentialId
    var publicKeyCose by CredentialInfos.publicKeyCose
    var signatureCount by CredentialInfos.signatureCount
}

fun CredentialInfo.toRegisteredCredential(): RegisteredCredential {
    return RegisteredCredential.builder()
        .credentialId(ByteArray.fromBase64(credentialId))
        .userHandle(ByteArray.fromBase64(user.userHandle))
        .publicKeyCose(ByteArray.fromBase64(publicKeyCose))
        .signatureCount(signatureCount)
        .build()
}

interface CredentialRepo : CredentialRepository {
    suspend fun findCredentialsByUser(user: UserInfo): Set<RegisteredCredential>
    suspend fun findCredentialsById(credentialId: ByteArray): Set<RegisteredCredential>
    suspend fun findCredentialsByUserAndId(user: UserInfo, credentialId: ByteArray): Optional<RegisteredCredential>
    suspend fun addRegistration(user: UserInfo, registration: RegisteredCredential): CredentialInfo
    suspend fun updateRegistration(email: String, credentialId: ByteArray, signatureCount: Long)
}


object CredentialRepoImpl : CredentialRepo {
    private val userRepo: UserRepo = UserRepoImpl
    override fun getCredentialIdsForUsername(email: String): Set<PublicKeyCredentialDescriptor> = runBlocking {
        val user = userRepo.findUserByEmail(email).getOrNull() ?: return@runBlocking emptySet()
        findCredentialsByUser(user).map {
            PublicKeyCredentialDescriptor.builder().id(it.credentialId).build()
        }.toSet()
    }

    override fun getUserHandleForUsername(email: String): Optional<ByteArray> = runBlocking {
        val user = userRepo.findUserByEmail(email)
        if (user.isPresent) Optional.of(ByteArray.fromBase64(user.get().userHandle))
        else Optional.empty()
    }

    override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> = runBlocking {
        val user = userRepo.findUserByUserHandle(userHandle)
        if (user.isPresent) Optional.of(user.get().email)
        else Optional.empty()
    }

    override fun lookup(credentialId: ByteArray, userHandle: ByteArray): Optional<RegisteredCredential> = runBlocking {
        val user = userRepo.findUserByUserHandle(userHandle).getOrNull() ?: return@runBlocking Optional.empty()
        findCredentialsByUserAndId(user, credentialId)
    }

    override fun lookupAll(credentialId: ByteArray): Set<RegisteredCredential> = runBlocking {
        findCredentialsById(credentialId)
    }

    override suspend fun findCredentialsByUser(user: UserInfo): Set<RegisteredCredential> = query {
        CredentialInfo
            .find { CredentialInfos.user eq user.id }
            .map { it.toRegisteredCredential() }.toSet()
    }

    override suspend fun findCredentialsById(credentialId: ByteArray): Set<RegisteredCredential> = query {
        CredentialInfo
            .find { CredentialInfos.credentialId eq credentialId.base64 }
            .map { it.toRegisteredCredential() }.toSet()
    }

    override suspend fun findCredentialsByUserAndId(user: UserInfo, credentialId: ByteArray): Optional<RegisteredCredential> = query {
        Optional.ofNullable(
            CredentialInfo
                .find { (CredentialInfos.credentialId eq credentialId.base64) and (CredentialInfos.user eq user.id) }
                .firstOrNull()
                ?.toRegisteredCredential()
        )
    }

    override suspend fun addRegistration(user: UserInfo, registration: RegisteredCredential): CredentialInfo = query {
        CredentialInfo.new {
            this.user = user
            this.credentialId = registration.credentialId.base64
            this.publicKeyCose = registration.publicKeyCose.base64
            this.signatureCount = registration.signatureCount
        }

    }

    override suspend fun updateRegistration(email: String, credentialId: ByteArray, signatureCount: Long) = query {
        val user = userRepo.findUserByEmail(email).getOrNull() ?: return@query
        val credential = CredentialInfo
            .find { (CredentialInfos.credentialId eq credentialId.base64) and (CredentialInfos.user eq user.id) }
            .firstOrNull() ?: return@query
        credential.signatureCount = signatureCount
    }
}