package com.littleetx.dao

import com.littleetx.plugins.query
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.UserIdentity
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import java.util.*

object UserInfos : IntIdTable() {
    val username = varchar("username", 50)
    val userHandle = varchar("user_handle", 200)
    val email = varchar("email", 50)
}

class UserInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserInfo>(UserInfos)

    var username by UserInfos.username
    var userHandle by UserInfos.userHandle
    var email by UserInfos.email
}

fun UserInfo.toUserIdentity(): UserIdentity {
    return UserIdentity.builder()
        .name(email)
        .displayName(username)
        .id(ByteArray.fromBase64(userHandle))
        .build()
}

interface UserRepo {
    suspend fun findUserByEmail(email: String): Optional<UserInfo>
    suspend fun findUserByUsername(username: String): Optional<UserInfo>
    suspend fun findUserByUserHandle(userHandle: ByteArray): Optional<UserInfo>
    suspend fun createUser(username: String, userHandle: ByteArray, email: String): UserInfo
}

val userRepo: UserRepo = UserRepoImpl

object UserRepoImpl : UserRepo {
    override suspend fun findUserByEmail(email: String): Optional<UserInfo> = query {
        Optional.ofNullable(UserInfo.find { UserInfos.email eq email }.firstOrNull())
    }

    override suspend fun findUserByUsername(username: String): Optional<UserInfo> = query {
        Optional.ofNullable(UserInfo.find { UserInfos.username eq username }.firstOrNull())
    }

    override suspend fun findUserByUserHandle(userHandle: ByteArray): Optional<UserInfo> = query {
        Optional.ofNullable(UserInfo.find { UserInfos.userHandle eq userHandle.base64 }.firstOrNull())
    }

    override suspend fun createUser(username: String, userHandle: ByteArray, email: String): UserInfo = query {
        UserInfo.new {
            this.username = username
            this.userHandle = userHandle.base64
            this.email = email
        }
    }

}