package com.littleetx.dao

import com.littleetx.plugins.query
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object PasswordInfos : IntIdTable() {
    val user = reference("user_id", UserInfos)
    val usePassword = bool("use_password")
    val password = varchar("password", 200)
}

class PasswordInfo(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PasswordInfo>(PasswordInfos)

    var user by UserInfo referencedOn PasswordInfos.user
    var usePassword by PasswordInfos.usePassword
    var password by PasswordInfos.password
}

interface PasswordRepo {
    suspend fun getPasswordInfo(user: UserInfo): PasswordInfo
    suspend fun setEnablePassword(user: UserInfo, enable: Boolean)
    suspend fun setPassword(user: UserInfo, password: String)
}

val passwordRepo: PasswordRepo = PasswordRepoImpl
object PasswordRepoImpl : PasswordRepo {
    override suspend fun getPasswordInfo(user: UserInfo): PasswordInfo = query {
        PasswordInfo.find { PasswordInfos.user eq user.id }.firstOrNull() ?: PasswordInfo.new {
            this.user = user
            this.usePassword = false
            this.password = ""
        }
    }

    override suspend fun setEnablePassword(user: UserInfo, enable: Boolean) = query {
        getPasswordInfo(user).usePassword = enable
    }

    override suspend fun setPassword(user: UserInfo, password: String) = query {
        getPasswordInfo(user).password = password
    }
}