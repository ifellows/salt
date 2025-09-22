package com.dev.salt.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the encryption key for the SQLCipher database.
 * Uses Android Keystore to securely store the key.
 */
object DatabaseKeyManager {
    private const val KEYSTORE_ALIAS = "SALT_DB_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "salt_security_prefs"
    private const val ENCRYPTED_KEY_PREF = "encrypted_db_key"
    private const val KEY_IV_PREF = "db_key_iv"
    
    /**
     * Gets or creates the database encryption passphrase.
     * The passphrase is encrypted using Android Keystore and stored in SharedPreferences.
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedKey = prefs.getString(ENCRYPTED_KEY_PREF, null)
        val iv = prefs.getString(KEY_IV_PREF, null)
        
        return if (encryptedKey != null && iv != null) {
            // Decrypt existing key
            decryptPassphrase(encryptedKey, iv)
        } else {
            // Generate new key and store it
            val newPassphrase = generateRandomPassphrase()
            storePassphrase(context, newPassphrase)
            newPassphrase
        }
    }
    
    private fun generateRandomPassphrase(): ByteArray {
        // Generate a strong random passphrase
        val passphrase = ByteArray(32) // 256 bits
        java.security.SecureRandom().nextBytes(passphrase)
        return passphrase
    }
    
    private fun storePassphrase(context: Context, passphrase: ByteArray) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // Generate or get the encryption key from Android Keystore
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            generateEncryptionKey()
        }
        
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        
        // Encrypt the passphrase
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val encryptedPassphrase = cipher.doFinal(passphrase)
        val iv = cipher.iv
        
        // Store encrypted passphrase and IV in SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(ENCRYPTED_KEY_PREF, Base64.encodeToString(encryptedPassphrase, Base64.NO_WRAP))
            .putString(KEY_IV_PREF, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }
    
    private fun decryptPassphrase(encryptedKey: String, ivString: String): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(ivString, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        val encryptedData = Base64.decode(encryptedKey, Base64.NO_WRAP)
        return cipher.doFinal(encryptedData)
    }
    
    private fun generateEncryptionKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }
    
    /**
     * Clears the stored database key (useful for security reset)
     */
    fun clearDatabaseKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(ENCRYPTED_KEY_PREF)
            .remove(KEY_IV_PREF)
            .apply()
        
        // Note: We don't delete the key from Keystore as it might be used for other purposes
        // The next getDatabasePassphrase() call will generate a new passphrase
    }
}