package com.off.audioconverter.ui

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last thing that went wrong, on the device and nowhere else.
 *
 * Off has no telemetry and no crash reporting service, so when something does go
 * wrong the only way to find out what happened is to write it down locally. The
 * log is a short, human-readable report the user can read from Settings and send
 * on if they choose to.
 */
object CrashLog {

    private const val FILE_NAME = "off-last-error.txt"
    private const val MAX_CHARS = 8000

    @Volatile private var installed = false

    /** Installs a handler that writes uncaught exceptions down before the app dies. */
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                write(app, "Uncaught exception on ${thread.name}", throwable)
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    /** Records a handled failure too, so it is not lost. */
    fun record(context: Context, what: String, error: Throwable?) {
        write(context.applicationContext, what, error)
    }

    private fun write(context: Context, what: String, error: Throwable?) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val body = buildString {
                appendLine("Off · $stamp")
                appendLine(what)
                if (error != null) {
                    appendLine("${error.javaClass.name}: ${error.message}")
                    appendLine(Log.getStackTraceString(error))
                }
            }
            File(context.filesDir, FILE_NAME).writeText(body.take(MAX_CHARS))
        }
    }

    /** The most recent report, or null when nothing has gone wrong. */
    fun read(context: Context): String? =
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
        }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }
}
