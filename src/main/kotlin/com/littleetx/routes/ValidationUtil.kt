package com.littleetx.routes

import java.security.MessageDigest

fun String.isValidEmail(): Boolean {
    return isNotEmpty() && matches(Regex("([a-zA-Z0-9_\\-.]+)@([a-zA-Z0-9_\\-.]+)\\.([a-zA-Z]{2,5})"))
}
fun String.isValidUsername(): Boolean {
    return isNotEmpty() && matches(Regex("[a-zA-Z0-9_]+"))
}

fun String.isValidPassword(): Boolean {
    return isNotEmpty() && length >= 8
}

@OptIn(ExperimentalStdlibApi::class)
fun encodePassword(password: String): String {
    val sha = MessageDigest.getInstance("SHA-256")
    sha.update(password.toByteArray())
    return sha.digest().toHexString()
}