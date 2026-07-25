package com.pontat.registreboucles

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.pontat.registreboucles.capture.CaptureActivity
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
        publierRaccourciCapture()
    }

    /**
     * Publie le raccourci de capture (Direct Share). La déclaration
     * `res/xml/shortcuts.xml` ne suffit pas : le système ne propose une cible
     * directe dans la feuille de partage que si un raccourci portant la MÊME
     * catégorie que le `<share-target>` a été publié à l'exécution.
     *
     * `pushDynamicShortcut` (et non `setDynamicShortcuts`) : on ajoute sans
     * écraser d'éventuels raccourcis futurs, et le classement d'usage du système
     * est préservé. Un échec n'a rien de bloquant — le partage générique
     * fonctionne de toute façon —, il ne doit donc jamais empêcher le démarrage.
     */
    private fun publierRaccourciCapture() {
        try {
            val cible = Intent(this, CaptureActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
            }
            val raccourci = ShortcutInfoCompat.Builder(this, "capture-dynamique")
                .setShortLabel(getString(R.string.raccourci_capture_court))
                .setLongLabel(getString(R.string.raccourci_capture_long))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_mnemosyne_grand))
                .setCategories(setOf("com.pontat.registreboucles.category.CAPTURE"))
                .setIntent(cible)
                .setLongLived(true)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, raccourci)
        } catch (e: Throwable) {
            Log.e("RegistreApplication", "Publication du raccourci de capture impossible", e)
        }
    }
}
