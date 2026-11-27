package br.com.ccortez.deliveryofflinefirst.contract

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaEntity
import br.com.ccortez.deliveryofflinefirst.data.local.ItemPedidoEntity
import br.com.ccortez.deliveryofflinefirst.data.repository.EntregaRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import org.junit.After

/**
 * Runs [EntregaRepositoryContractTest] against the real, Room-backed [EntregaRepositoryImpl] —
 * the same production class bound in `AppModule`. Arrangement writes directly to the DAO
 * (bypassing `EntregaRepository.inserirTodas`/`inserirItens`) the same way `ConferirItemOutboxTest`
 * does, so this stays a test of [EntregaRepositoryImpl.concluirEntrega] specifically.
 *
 * [ApplicationProvider.getApplicationContext] resolves to a Robolectric-provided context when
 * run under `RobolectricTestRunner` (unit test tree) or to the real instrumentation context when
 * run under `AndroidJUnit4` (instrumented tree) — leaf subclasses only need to pick the runner.
 */
abstract class RoomEntregaRepositoryContractTest : EntregaRepositoryContractTest() {

    private var db: AppDatabase? = null

    @After
    fun fecharBancoEmMemoria() {
        db?.close()
    }

    override suspend fun repositorioCom(entregaId: String, itens: List<ItemPedido>): EntregaRepository {
        val dao = criarDaoEmMemoria()
        dao.inserirTodas(
            listOf(
                EntregaEntity(
                    id = entregaId,
                    cliente = "Contrato",
                    endereco = "Rua Contrato",
                    status = "Pendente",
                    uuid = "uuid-$entregaId"
                )
            )
        )
        dao.inserirItens(itens.map { it.toEntity(entregaId) })
        return EntregaRepositoryImpl(dao)
    }

    override suspend fun repositorioVazio(): EntregaRepository =
        EntregaRepositoryImpl(criarDaoEmMemoria())

    private fun criarDaoEmMemoria(): EntregaDao {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val novoBanco = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db = novoBanco
        return novoBanco.entregaDao()
    }

    private fun ItemPedido.toEntity(entregaId: String) = ItemPedidoEntity(
        id = id, entregaId = entregaId, nome = nome, quantidade = quantidade, conferido = conferido
    )
}
