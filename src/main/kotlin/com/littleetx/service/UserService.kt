package com.littleetx.service

import com.littleetx.dao.CredentialInfo
import com.littleetx.dao.CredentialRepo
import com.littleetx.dao.CredentialRepoImpl
import com.littleetx.dao.UserInfo
import com.littleetx.plugins.UserSession
import java.util.*

interface UserService {
    suspend fun login(session: UserSession, user: UserInfo)
    suspend fun logout(session: UserSession)
    suspend fun getUserInfo(session: UserSession): Optional<UserInfo>
    suspend fun getUserCredentials(session: UserSession): Set<CredentialInfo>
}

object UserServiceImpl : UserService {
    private val loginSessions = mutableMapOf<UserSession, UserInfo>()
    private val credentialRepo: CredentialRepo = CredentialRepoImpl
    override suspend fun login(session: UserSession, user: UserInfo) {
        loginSessions[session] = user
    }

    override suspend fun logout(session: UserSession) {
        loginSessions.remove(session)
    }

    override suspend fun getUserInfo(session: UserSession): Optional<UserInfo> {
        return Optional.ofNullable(loginSessions[session])
    }

    override suspend fun getUserCredentials(session: UserSession): Set<CredentialInfo> {
        val user = loginSessions[session] ?: return emptySet()
        return credentialRepo.findCredentialsByUser(user)
    }
}