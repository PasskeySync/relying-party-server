package com.littleetx.plugins

import com.littleetx.dao.CredentialInfos
import com.littleetx.dao.PasswordInfos
import com.littleetx.dao.UserInfos
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction


val tables = arrayOf(
    UserInfos,
    CredentialInfos,
    PasswordInfos,
)

fun Application.configureDatabase() {
    val url = environment.config.property("db.url").getString()
    val driver = environment.config.property("db.driver").getString()
    val database = Database.connect(url, driver)
    transaction(database) {
        SchemaUtils.create(*tables)
    }
}

suspend fun <T> query(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }