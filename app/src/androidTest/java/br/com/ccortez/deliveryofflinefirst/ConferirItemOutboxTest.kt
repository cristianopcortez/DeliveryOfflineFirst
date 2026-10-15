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
        assertEquals(listOf("e1"), dao.listarPendentes().map { it.entrega.id })
    }

    // ── R2: a delivery can NEVER be concluded with unchecked items ─────────────────

    @Test
    fun concluirEntrega_recusadaQuandoHaItensPendentes() = runBlocking {
        dao.inserirTodas(listOf(EntregaEntity("e1", "Ana", "Rua A", "Pendente", uuid = "u1")))
        dao.inserirItens(
            listOf(
                ItemPedidoEntity("i1", "e1", "A", 1, conferido = true),
                ItemPedidoEntity("i2", "e1", "B", 1, conferido = false)
            )
        )

        assertEquals(0, dao.concluirEntregaSeConferida("e1", 1L))
        assertEquals(1, dao.contarItensPendentes("e1"))
        assertEquals("Pendente", dao.observarTodas().first().single().entrega.status)
    }

    @Test
    fun concluirEntrega_permitidaQuandoTodosItensConferidos_eEntraNoOutbox() = runBlocking {
        dao.inserirTodas(listOf(EntregaEntity("e1", "Ana", "Rua A", "Pendente", uuid = "u1")))
        dao.inserirItens(listOf(ItemPedidoEntity("i1", "e1", "A", 1, conferido = true)))

        assertEquals(1, dao.concluirEntregaSeConferida("e1", 1L))

        val entrega = dao.observarTodas().first().single().entrega
        assertEquals("Concluída", entrega.status)
        assertFalse(entrega.sincronizada)
    }

    // ── Sync payload: pending deliveries carry their items ─────────────────────────

    @Test
    fun listarPendentes_incluiItensDaEntrega() = runBlocking {
        dao.inserirTodas(listOf(EntregaEntity("e1", "Ana", "Rua A", "Pendente", sincronizada = false, uuid = "u1")))
        dao.inserirItens(
            listOf(
                ItemPedidoEntity("i1", "e1", "A", 1, conferido = true),
                ItemPedidoEntity("i2", "e1", "B", 2, conferido = false)
            )
        )

        val pendentes = dao.listarPendentes()

        assertEquals(1, pendentes.size)
        assertEquals(setOf("i1", "i2"), pendentes.single().itens.map { it.id }.toSet())
    }

    // ── R1: seed is all-or-nothing and only runs on an empty database ──────────────

    @Test
    fun semearSeVazio_gravaEntregasEItensEUmaUnicaVez() = runBlocking {
        val entregas = listOf(EntregaEntity("s1", "Ana", "Rua A", "Pendente", uuid = "u1"))
        val itens = listOf(ItemPedidoEntity("s1-a", "s1", "A", 1, conferido = false))

        assertTrue(dao.semearSeVazio(entregas, itens))
        assertFalse("second seed must be a no-op", dao.semearSeVazio(entregas, itens))
        assertEquals(1, dao.observarTodas().first().single().itens.size)
    }

    @Test
    fun semearSeVazio_falhaNoMeioNaoDeixaEntregasSemItens() = runBlocking {
        val entregas = listOf(EntregaEntity("s1", "Ana", "Rua A", "Pendente", uuid = "u1"))
        // FK violation: item points to a delivery that does not exist -> whole transaction rolls back.
        val itensInvalidos = listOf(ItemPedidoEntity("x-a", "inexistente", "A", 1, conferido = false))

        runCatching { dao.semearSeVazio(entregas, itensInvalidos) }

        assertEquals("rollback expected: no orphan deliveries", 0, dao.contarEntregas())
    }
}
