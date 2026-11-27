package br.com.ccortez.deliveryofflinefirst.contract

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.fake.FakeEntregaRepository
import org.junit.runner.RunWith

/**
 * [EntregaRepositoryContractTest] run against the instrumented-test fake used by Compose UI
 * tests (`FakeEntregaRepository`). Confirms the fix keeps honoring the invariant on-device too.
 */
@RunWith(AndroidJUnit4::class)
class FakeEntregaRepositoryContractTest : EntregaRepositoryContractTest() {

    override suspend fun repositorioCom(entregaId: String, itens: List<ItemPedido>): EntregaRepository =
        FakeEntregaRepository(
            initialData = listOf(
                EntregaComProdutos(
                    entrega = Entrega(
                        id = entregaId,
                        cliente = "Contrato",
                        endereco = "Rua Contrato",
                        status = "Pendente"
                    ),
                    itens = itens
                )
            )
        )

    override suspend fun repositorioVazio(): EntregaRepository =
        FakeEntregaRepository(initialData = emptyList())
}
