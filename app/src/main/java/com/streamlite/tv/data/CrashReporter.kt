package com.streamlite.tv.data

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private const val FILE = "crash_report.txt"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val report = "CRASH in thread '${thread.name}':\n$throwable\n\n${sw}"
                appCtx.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(report.toByteArray().take(12000).toByteArray())
                }
            } catch (_: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun takeReport(context: Context): String? {
        return try {
            context.openFileInput(FILE).bufferedReader().readText().also {
                context.deleteFile(FILE)
            }
        } catch (_: Exception) { null }
    }
}
