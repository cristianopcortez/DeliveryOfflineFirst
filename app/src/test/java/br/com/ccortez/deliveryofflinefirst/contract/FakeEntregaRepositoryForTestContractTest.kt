package br.com.ccortez.deliveryofflinefirst.contract

import br.com.ccortez.deliveryofflinefirst.domain.model.Entrega
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.model.ItemPedido
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.fake.FakeEntregaRepositoryForTest

/**
 * [EntregaRepositoryContractTest] run against the unit-test fake. Plain JVM — no Robolectric
 * needed since [FakeEntregaRepositoryForTest] touches no Android API.
 */
class FakeEntregaRepositoryForTestContractTest : EntregaRepositoryContractTest() {

    override suspend fun repositorioCom(entregaId: String, itens: List<ItemPedido>): EntregaRepository =
        FakeEntregaRepositoryForTest(
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
        FakeEntregaRepositoryForTest(initialData = emptyList())
}
