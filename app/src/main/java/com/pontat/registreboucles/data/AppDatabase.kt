package com.pontat.registreboucles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Boucle::class, Mouvement::class, Journal::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun boucleDao(): BoucleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2 : ajout de la colonne `milieu` (préserve les données existantes).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boucles ADD COLUMN milieu TEXT")
            }
        }

        // v2 -> v3 : table `journaux` (journal de clôture).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `journaux` (" +
                        "`journalId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`boucleId` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `texte` TEXT NOT NULL, " +
                        "FOREIGN KEY(`boucleId`) REFERENCES `boucles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_journaux_boucleId` ON `journaux` (`boucleId`)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "registre-boucles.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
