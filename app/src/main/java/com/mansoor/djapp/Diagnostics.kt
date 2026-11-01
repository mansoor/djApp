package com.mansoor.djapp

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Records crashes and reads back Android's record of the last freeze (ANR), so problems
 * on a sideloaded phone can be reported without adb.
 */
object Diagnostics {
    private const val PREFS = "diag"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("crash", "Crash on thread ${thread.name}:\n${e.stackTraceToString().take(6000)}")
                    .commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Returns a report of a crash/freeze since the last time this was called, if any. */
    fun takeLastProblem(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.getString("crash", null)?.let {
            sp.edit().remove("crash").apply()
            return it
        }
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val am = ctx.getSystemService(ActivityManager::class.java)
            val info = am.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull() ?: return null
            if (info.reason != ApplicationExitInfo.REASON_ANR) return null
            if (info.timestamp <= sp.getLong("seenAnr", 0)) return null
            sp.edit().putLong("seenAnr", info.timestamp).apply()
            val trace = info.traceInputStream?.bufferedReader()?.use { it.readText() } ?: ""
            "App froze (ANR): ${info.description}\n\n" + mainThread(trace)
        } catch (e: Exception) {
            null
        }
    }

    private fun mainThread(trace: String): String {
        val start = trace.indexOf("\"main\"")
        if (start < 0) return trace.take(4000)
        val end = trace.indexOf("\n\n", start).let { if (it < 0) trace.length else it }
        return trace.substring(start, end).lines().take(45).joinToString("\n")
    }
}
