package br.com.ccortez.deliveryofflinefirst.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntregaEntity::class, ItemPedidoEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entregaDao(): EntregaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entrega ADD COLUMN horarioConclusao INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entrega ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
        }

        // Creates the item_pedido table with FK CASCADE to preserve existing offline delivery data.
        // fallbackToDestructiveMigration() is intentionally absent — existing rows are kept intact.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `item_pedido` (
                        `id`         TEXT    NOT NULL,
                        `entregaId`  TEXT    NOT NULL,
                        `nome`       TEXT    NOT NULL,
                        `quantidade` INTEGER NOT NULL,
                        `conferido`  INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`entregaId`) REFERENCES `entrega`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_item_pedido_entregaId` ON `item_pedido` (`entregaId`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
