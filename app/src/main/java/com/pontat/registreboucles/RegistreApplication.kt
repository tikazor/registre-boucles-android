package com.pontat.registreboucles

import android.app.Application
import com.pontat.registreboucles.data.AppDatabase
import com.pontat.registreboucles.data.BoucleRepository
import java.io.File
import java.util.Date

class RegistreApplication : Application() {

    lateinit var repository: BoucleRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Capture le handler système AVANT de le remplacer, pour toujours le
        // relancer ensuite (ne jamais avaler le crash).
        val handlerSysteme = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = File(applicationContext.filesDir, "crash.log")
                logFile.appendText("${Date()} — ${throwable.stackTraceToString()}\n\n")
            } catch (_: Throwable) {
                // On n'empêche jamais la remontée du crash à cause du log.
            }
            handlerSysteme?.uncaughtException(thread, throwable)
        }

        repository = BoucleRepository(AppDatabase.get(this).boucleDao(), this)
    }
}
