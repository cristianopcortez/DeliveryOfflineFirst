package br.com.ccortez.deliveryofflinefirst

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates Room migrations against the exported schemas in app/schemas.
 * Guardrail: no fallbackToDestructiveMigration — offline deliveries must survive upgrades.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4_preservesDeliveries_andCreatesItemTableWithIndex() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO entrega (id, cliente, endereco, status, sincronizada, horarioConclusao, uuid) " +
                    "VALUES ('e1', 'Ana', 'Rua A', 'Pendente', 0, NULL, 'uuid-1')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)

        // Existing offline delivery (and its pending-sync flag) is intact.
        db.query("SELECT cliente, sincronizada, uuid FROM entrega WHERE id = 'e1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ana", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals("uuid-1", c.getString(2))
        }

        // New table is usable and FK column is indexed.
        db.execSQL(
            "INSERT INTO item_pedido (id, entregaId, nome, quantidade, conferido) VALUES ('i1', 'e1', 'Item', 1, 0)"
        )
        db.query("PRAGMA index_list('item_pedido')").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            assertTrue(names.contains("index_item_pedido_entregaId"))
        }
    }

    @Test
    fun migrateAll_1To4_validatesSchema() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 4, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4
        ).close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
