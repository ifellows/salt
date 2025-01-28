package com.dev.salt

import java.security.MessageDigest
import kotlin.random.Random

fun randomHash(): String {
    // Generate a random number
    val randomNumber = Random.nextInt()

    // Convert the number to a string
    val randomString = randomNumber.toString()

    // Compute the MD5 hash
    val md = MessageDigest.getInstance("MD5")
    val hashBytes = md.digest(randomString.toByteArray())

    // Convert the hash bytes to a hexadecimal string
    return hashBytes.joinToString("") { "%02x".format(it) }
}