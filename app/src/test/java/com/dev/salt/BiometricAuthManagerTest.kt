package com.dev.salt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dev.salt.auth.BiometricAuthManager
import com.dev.salt.auth.BiometricResult
import com.dev.salt.data.User
import com.dev.salt.data.UserDao
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BiometricAuthManagerTest {

    private lateinit var context: Context
    private lateinit var userDao: UserDao
    private lateinit var biometricAuthManager: BiometricAuthManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        userDao = mock()
        biometricAuthManager = BiometricAuthManager(context, userDao)
    }

    @Test
    fun `isBiometricSupported returns true for mock implementation`() {
        // Mock implementation always returns true
        assertTrue(biometricAuthManager.isBiometricSupported())
    }

    @Test
    fun `enrollUserBiometric succeeds for valid user`() = runBlocking {
        // Given
        val userName = "testuser"
        
        // When
        val result = biometricAuthManager.enrollUserBiometric(userName)
        
        // Then
        assertTrue(result is BiometricResult.Success)
        verify(userDao).updateUserBiometric(
            eq(userName),
            any(),
            eq(true),
            any()
        )
    }

    @Test
    fun `authenticateUserBiometric succeeds for enrolled user`() = runBlocking {
        // Given
        val userName = "testuser"
        val mockUser = User(
            userName = userName,
            hashedPassword = "hashedpass",
            fullName = "Test User",
            role = "SURVEY_STAFF",
            biometricEnabled = true,
            biometricKeyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" // SHA-256 of "testtesttest"
        )
        
        whenever(userDao.getUserForBiometricAuth(userName)).thenReturn(mockUser)
        
        // When
        var authResult: BiometricResult? = null
        biometricAuthManager.authenticateUserBiometric(userName) { result ->
            authResult = result
        }
        
        // Then
        assertTrue(authResult is BiometricResult.Success)
        verify(userDao).updateLastBiometricAuth(eq(userName), any())
    }

    @Test
    fun `authenticateUserBiometric fails for non-enrolled user`() = runBlocking {
        // Given
        val userName = "testuser"
        
        whenever(userDao.getUserForBiometricAuth(userName)).thenReturn(null)
        
        // When
        var authResult: BiometricResult? = null
        biometricAuthManager.authenticateUserBiometric(userName) { result ->
            authResult = result
        }
        
        // Then
        assertTrue(authResult is BiometricResult.Error)
        verify(userDao, never()).updateLastBiometricAuth(any(), any())
    }

    @Test
    fun `disableBiometricForUser succeeds`() = runBlocking {
        // Given
        val userName = "testuser"
        
        // When
        val result = biometricAuthManager.disableBiometricForUser(userName)
        
        // Then
        assertTrue(result is BiometricResult.Success)
        verify(userDao).updateUserBiometric(
            eq(userName),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `showBiometricPrompt immediately succeeds in mock mode`() {
        // Given
        var promptResult: BiometricResult? = null
        
        // When
        biometricAuthManager.showBiometricPrompt(
            title = "Test Title",
            subtitle = "Test Subtitle"
        ) { result ->
            promptResult = result
        }
        
        // Then
        assertTrue(promptResult is BiometricResult.Success)
    }
}