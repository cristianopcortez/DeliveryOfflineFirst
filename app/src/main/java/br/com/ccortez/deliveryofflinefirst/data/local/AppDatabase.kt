package br.com.ccortez.deliveryofflinefirst.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EntregaEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entregaDao(): EntregaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 → v2: adds horarioConclusao (nullable INTEGER).
        // Existing rows keep all data; new column defaults to NULL.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE entrega ADD COLUMN horarioConclusao INTEGER"
                )
            }
        }

        // v2 → v3: adds uuid (TEXT NOT NULL DEFAULT '').
        // Empty string for existing rows is intentional — those are seed/mock deliveries
        // that were never in a real outbox. New deliveries always receive a UUID
        // from EntregaRepositoryImpl before being persisted.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE entrega ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "entregas.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
