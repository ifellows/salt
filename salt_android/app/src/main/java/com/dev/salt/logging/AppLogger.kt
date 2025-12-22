package com.dev.salt.logging

import android.content.Context
import android.util.Log as AndroidLog
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppLogger {
    private const val MAX_LOG_FILE_SIZE = 10 * 1024 * 1024 // 10MB
    private const val MAX_LOG_FILES = 5
    private const val LOG_DIR_NAME = "dev_logs"
    private const val CURRENT_LOG_FILE = "salt_dev_log.current.txt"

    // Hardcoded for now (future: read from settings)
    private var isFileLoggingEnabled = true
    private var isLogcatEnabled = true

    private var logDir: File? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mutex = Mutex()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        // Load settings from SharedPreferences
        val prefs = context.getSharedPreferences("dev_settings", Context.MODE_PRIVATE)
        isFileLoggingEnabled = prefs.getBoolean("file_logging_enabled", true)
        isLogcatEnabled = prefs.getBoolean("logcat_enabled", true)

        logDir = File(context.filesDir, LOG_DIR_NAME)
        val created = logDir?.mkdirs() ?: false
        val exists = logDir?.exists() ?: false

        // Log to Android logcat for debugging
        AndroidLog.i("AppLogger", "Initializing AppLogger:")
        AndroidLog.i("AppLogger", "  Log directory: ${logDir?.absolutePath}")
        AndroidLog.i("AppLogger", "  Directory created: $created")
        AndroidLog.i("AppLogger", "  Directory exists: $exists")
        AndroidLog.i("AppLogger", "  File logging enabled: $isFileLoggingEnabled")
        AndroidLog.i("AppLogger", "  Logcat enabled: $isLogcatEnabled")

        // Test file write
        if (exists) {
            try {
                val testFile = getCurrentLogFile()
                AndroidLog.i("AppLogger", "  Test file path: ${testFile?.absolutePath}")
                testFile?.let {
                    FileWriter(it, true).use { writer ->
                        writer.write("[INIT] AppLogger initialized at ${dateFormatter.format(Date())}\n")
                    }
                    AndroidLog.i("AppLogger", "  Test write successful, file size: ${it.length()} bytes")
                }
            } catch (e: Exception) {
                AndroidLog.e("AppLogger", "  Test write FAILED", e)
            }
        }
    }

    // Log methods matching android.util.Log API
    fun v(tag: String, msg: String): Int {
        return log(Level.VERBOSE, tag, msg, null)
    }

    fun d(tag: String, msg: String): Int {
        return log(Level.DEBUG, tag, msg, null)
    }

    fun i(tag: String, msg: String): Int {
        return log(Level.INFO, tag, msg, null)
    }

    fun w(tag: String, msg: String): Int {
        return log(Level.WARN, tag, msg, null)
    }

    fun e(tag: String, msg: String): Int {
        return log(Level.ERROR, tag, msg, null)
    }

    fun wtf(tag: String, msg: String): Int {
        return log(Level.ASSERT, tag, msg, null)
    }

    // Overloads with throwable
    fun d(tag: String, msg: String, tr: Throwable?): Int = log(Level.DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable?): Int = log(Level.INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable?): Int = log(Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable?): Int = log(Level.ERROR, tag, msg, tr)

    private fun log(level: Level, tag: String, msg: String, tr: Throwable?): Int {
        // Write to logcat (always for now)
        if (isLogcatEnabled) {
            when (level) {
                Level.VERBOSE -> AndroidLog.v(tag, msg, tr)
                Level.DEBUG -> AndroidLog.d(tag, msg, tr)
                Level.INFO -> AndroidLog.i(tag, msg, tr)
                Level.WARN -> AndroidLog.w(tag, msg, tr)
                Level.ERROR -> AndroidLog.e(tag, msg, tr)
                Level.ASSERT -> AndroidLog.wtf(tag, msg, tr)
            }
        }

        // Write to file asynchronously
        if (isFileLoggingEnabled) {
            executor.execute {
                writeToFile(level, tag, msg, tr)
            }
        }

        return 0 // Match android.util.Log return value
    }

    private fun writeToFile(level: Level, tag: String, msg: String, tr: Throwable?) {
        val logFile = getCurrentLogFile() ?: return

        // Check rotation
        if (logFile.length() >= MAX_LOG_FILE_SIZE) {
            rotateLogFiles()
        }

        // Format log entry
        val timestamp = dateFormatter.format(Date())
        val levelChar = when (level) {
            Level.VERBOSE -> "V"
            Level.DEBUG -> "D"
            Level.INFO -> "I"
            Level.WARN -> "W"
            Level.ERROR -> "E"
            Level.ASSERT -> "A"
        }

        val stackTrace = tr?.let { "\n" + AndroidLog.getStackTraceString(it) } ?: ""
        val logEntry = "[$timestamp] [$levelChar/$tag] $msg$stackTrace\n"

        // Append to file
        try {
            FileWriter(logFile, true).use { it.write(logEntry) }
        } catch (e: IOException) {
            AndroidLog.e("AppLogger", "Failed to write log", e)
        }
    }

    private fun getCurrentLogFile(): File? {
        return logDir?.let { File(it, CURRENT_LOG_FILE) }
    }

    private fun rotateLogFiles() {
        val currentFile = getCurrentLogFile() ?: return

        // Shift existing rotated files
        for (i in (MAX_LOG_FILES - 1) downTo 0) {
            val oldFile = File(logDir, "salt_dev_log.$i.txt")
            if (oldFile.exists()) {
                if (i == MAX_LOG_FILES - 1) {
                    oldFile.delete() // Delete oldest
                } else {
                    val newFile = File(logDir, "salt_dev_log.${i + 1}.txt")
                    oldFile.renameTo(newFile)
                }
            }
        }

        // Rotate current file
        val rotatedFile = File(logDir, "salt_dev_log.0.txt")
        currentFile.renameTo(rotatedFile)
    }

    // Log collection for upload (only latest file)
    suspend fun collectLatestLogs(): String? {
        return mutex.withLock {
            val currentFile = getCurrentLogFile()
            if (currentFile?.exists() == true && currentFile.length() > 0) {
                currentFile.readText()
            } else {
                null
            }
        }
    }

    fun getCurrentLogSize(): Long {
        return getCurrentLogFile()?.length() ?: 0L
    }

    fun clearLogs() {
        executor.execute {
            logDir?.listFiles()?.forEach { it.delete() }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    // Update settings from SharedPreferences
    fun updateSettings(context: Context) {
        val prefs = context.getSharedPreferences("dev_settings", Context.MODE_PRIVATE)
        isFileLoggingEnabled = prefs.getBoolean("file_logging_enabled", true)
        isLogcatEnabled = prefs.getBoolean("logcat_enabled", true)

        AndroidLog.i("AppLogger", "Settings updated: fileLogging=$isFileLoggingEnabled, logcat=$isLogcatEnabled")
    }

    private enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT
    }
}
