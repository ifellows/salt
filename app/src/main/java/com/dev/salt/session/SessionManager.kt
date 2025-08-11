package com.dev.salt.session

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class SessionState(
    val isActive: Boolean = false,
    val userName: String? = null,
    val sessionStartTime: Long = 0L,
    val lastActivityTime: Long = 0L,
    val sessionTimeout: Long = 30 * 60 * 1000L, // 30 minutes in milliseconds
    val warningThreshold: Long = 5 * 60 * 1000L // 5 minutes warning before timeout
)

sealed class SessionEvent {
    object SessionExpired : SessionEvent()
    object SessionWarning : SessionEvent()
    data class SessionExtended(val newExpirationTime: Long) : SessionEvent()
}

class SessionManager {
    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()
    
    private val _sessionEvents = MutableStateFlow<SessionEvent?>(null)
    val sessionEvents: StateFlow<SessionEvent?> = _sessionEvents.asStateFlow()
    
    private var sessionTimeoutJob: Job? = null
    private var warningTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "SessionManager"
        private const val DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000L // 30 minutes
        private const val DEFAULT_WARNING_THRESHOLD = 5 * 60 * 1000L // 5 minutes
    }
    
    fun startSession(userName: String, customTimeout: Long? = null) {
        val currentTime = System.currentTimeMillis()
        val timeout = customTimeout ?: DEFAULT_SESSION_TIMEOUT
        
        Log.d(TAG, "Starting session for user: $userName")
        
        _sessionState.value = SessionState(
            isActive = true,
            userName = userName,
            sessionStartTime = currentTime,
            lastActivityTime = currentTime,
            sessionTimeout = timeout,
            warningThreshold = DEFAULT_WARNING_THRESHOLD
        )
        
        scheduleSessionTimeouts()
    }
    
    fun endSession() {
        Log.d(TAG, "Ending session for user: ${_sessionState.value.userName}")
        
        sessionTimeoutJob?.cancel()
        warningTimeoutJob?.cancel()
        
        _sessionState.value = SessionState()
        _sessionEvents.value = null
    }
    
    fun logout() {
        Log.d(TAG, "User logout initiated for user: ${_sessionState.value.userName}")
        endSession()
    }
    
    fun extendSession() {
        val currentState = _sessionState.value
        if (!currentState.isActive) return
        
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "Extending session for user: ${currentState.userName}")
        
        _sessionState.value = currentState.copy(
            lastActivityTime = currentTime
        )
        
        // Cancel existing timeout jobs and reschedule
        sessionTimeoutJob?.cancel()
        warningTimeoutJob?.cancel()
        scheduleSessionTimeouts()
        
        _sessionEvents.value = SessionEvent.SessionExtended(currentTime + currentState.sessionTimeout)
    }
    
    fun isSessionActive(): Boolean {
        return _sessionState.value.isActive
    }
    
    fun getCurrentUser(): String? {
        return if (_sessionState.value.isActive) _sessionState.value.userName else null
    }
    
    fun getTimeUntilExpiration(): Long {
        val currentState = _sessionState.value
        if (!currentState.isActive) return 0L
        
        val currentTime = System.currentTimeMillis()
        val expirationTime = currentState.lastActivityTime + currentState.sessionTimeout
        return maxOf(0L, expirationTime - currentTime)
    }
    
    fun getTimeUntilWarning(): Long {
        val currentState = _sessionState.value
        if (!currentState.isActive) return 0L
        
        val currentTime = System.currentTimeMillis()
        val warningTime = currentState.lastActivityTime + currentState.sessionTimeout - currentState.warningThreshold
        return maxOf(0L, warningTime - currentTime)
    }
    
    fun clearSessionEvent() {
        _sessionEvents.value = null
    }
    
    private fun scheduleSessionTimeouts() {
        val currentState = _sessionState.value
        if (!currentState.isActive) return
        
        val timeUntilWarning = getTimeUntilWarning()
        val timeUntilExpiration = getTimeUntilExpiration()
        
        Log.d(TAG, "Scheduling session timeouts - Warning in: ${timeUntilWarning}ms, Expiration in: ${timeUntilExpiration}ms")
        
        // Schedule warning
        if (timeUntilWarning > 0) {
            warningTimeoutJob = scope.launch {
                delay(timeUntilWarning)
                if (_sessionState.value.isActive) {
                    Log.d(TAG, "Session warning triggered for user: ${_sessionState.value.userName}")
                    _sessionEvents.value = SessionEvent.SessionWarning
                }
            }
        }
        
        // Schedule expiration
        if (timeUntilExpiration > 0) {
            sessionTimeoutJob = scope.launch {
                delay(timeUntilExpiration)
                if (_sessionState.value.isActive) {
                    Log.d(TAG, "Session expired for user: ${_sessionState.value.userName}")
                    _sessionEvents.value = SessionEvent.SessionExpired
                    endSession()
                }
            }
        }
    }
    
    fun cleanup() {
        sessionTimeoutJob?.cancel()
        warningTimeoutJob?.cancel()
        scope.cancel()
    }
}

// Singleton instance for global access
object SessionManagerInstance {
    val instance: SessionManager by lazy { SessionManager() }
}