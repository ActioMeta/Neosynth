package com.example.neosynth.utils

import java.security.MessageDigest
import java.util.UUID

object AuthUtils {

    fun generateSalt(): String = UUID.randomUUID().toString().take(6)

    fun generateToken(password: String, salt: String): String {
        val input = password + salt
        return md5(input)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}