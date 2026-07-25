package com.pontat.registreboucles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Boucle::class, Mouvement::class, Journal::class, Suppression::class,
        EvenementSync::class, Capture::class
    ],
    version = 7,
    exportSchema = true
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

        // v3 -> v4 : colonne `source` (provenance). null = user (historique).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boucles ADD COLUMN source TEXT")
            }
        }

        // v4 -> v5 : horodatage de modification (qui / quand) + table des
        // suppressions (tombstones). Deux ALTER TABLE : la table `boucles` n'est
        // pas recréée, les données existantes sont conservées telles quelles
        // (modifieeLe null se lit comme `creee`, cf. Boucle.derniereModification).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boucles ADD COLUMN modifieeLe INTEGER")
                db.execSQL("ALTER TABLE boucles ADD COLUMN modifieePar TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suppressions` (" +
                        "`boucleId` TEXT NOT NULL, `supprimeeLe` INTEGER NOT NULL, " +
                        "`supprimeePar` TEXT NOT NULL, PRIMARY KEY(`boucleId`))"
                )
            }
        }

        // v5 -> v6 : table `evenements_sync` (journal de synchronisation). Aucune
        // table existante n'est touchée : une base v5 devient v6 sans réécriture.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evenements_sync` (" +
                        "`evenementId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`horodatage` INTEGER NOT NULL, `appareilDistant` TEXT NOT NULL, " +
                        "`fichierLu` TEXT NOT NULL, `exporteLeDistant` INTEGER, " +
                        "`bouclesAjoutees` INTEGER NOT NULL, `bouclesFusionnees` INTEGER NOT NULL, " +
                        "`bouclesIgnorees` INTEGER NOT NULL, `mouvementsAjoutes` INTEGER NOT NULL, " +
                        "`journauxAjoutes` INTEGER NOT NULL, `conflits` INTEGER NOT NULL, " +
                        "`resultat` TEXT NOT NULL, `detail` TEXT NOT NULL)"
                )
            }
        }

        // v6 -> v7 : table `captures` (boîte de réception des notes brutes) et ses
        // deux index. Aucune table existante n'est touchée. Pas de clé étrangère
        // vers `boucles` : une capture ne doit jamais disparaître en cascade
        // (cf. Capture.boucleLiee et invariant I14).
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `captures` (" +
                        "`id` TEXT NOT NULL, `contenuBrut` TEXT NOT NULL, `titre` TEXT, " +
                        "`appareil` TEXT NOT NULL, `appSource` TEXT, " +
                        "`capturee` INTEGER NOT NULL, `empreinte` TEXT NOT NULL, " +
                        "`statut` TEXT NOT NULL, `boucleLiee` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_empreinte` ON `captures` (`empreinte`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_captures_statut` ON `captures` (`statut`)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "registre-boucles.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7
                )
                    .build().also { INSTANCE = it }
            }
    }
}
