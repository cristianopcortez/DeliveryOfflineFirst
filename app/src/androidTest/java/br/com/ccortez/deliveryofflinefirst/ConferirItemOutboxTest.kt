package br.com.ccortez.deliveryofflinefirst

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaEntity
import br.com.ccortez.deliveryofflinefirst.data.local.ItemPedidoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** B2: checking an item must flag the parent delivery as pending sync (outbox). */
@RunWith(AndroidJUnit4::class)
class ConferirItemOutboxTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EntregaDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.entregaDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun atualizarConferido_marcaEntregaPaiComoPendente() = runBlocking {
        dao.inserirTodas(
            listOf(
                EntregaEntity("e1", "Ana", "Rua A", "Pendente", sincronizada = true, uuid = "u1"),
                EntregaEntity("e2", "Beto", "Rua B", "Pendente", sincronizada = true, uuid = "u2")
            )
        )
        dao.inserirItens(
            listOf(
                ItemPedidoEntity("i1", "e1", "Item 1", 1, conferido = false),
                ItemPedidoEntity("i2", "e2", "Item 2", 1, conferido = false)
            )
        )

        dao.atualizarConferidoMarcandoPendente("i1", true)

        val todas = dao.observarTodas().first().associateBy { it.entrega.id }
        assertTrue(todas.getValue("e1").itens.single().conferido)
        assertFalse("parent must be pending sync", todas.getValue("e1").entrega.sincronizada)
        assertTrue("unrelated delivery must stay synced", todas.getValue("e2").entrega.sincronizada)
        assertEquals(listOf("e1"), dao.listarPendentes().map { it.id })
    }
}
