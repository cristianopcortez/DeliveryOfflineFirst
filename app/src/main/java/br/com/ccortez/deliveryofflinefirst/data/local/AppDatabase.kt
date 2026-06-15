package br.com.ccortez.deliveryofflinefirst.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EntregaEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entregaDao(): EntregaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Adds horarioConclusao (nullable INTEGER) without touching existing rows.
        // fallbackToDestructiveMigration() is intentionally absent — dropping user data
        // (pending deliveries not yet synced) is not acceptable in a field app.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE entrega ADD COLUMN horarioConclusao INTEGER"
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
