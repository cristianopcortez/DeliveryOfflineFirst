package br.com.ccortez.deliveryofflinefirst.presentation.nlp

import br.com.ccortez.deliveryofflinefirst.domain.analytics.AnalyticsEvents
import br.com.ccortez.deliveryofflinefirst.domain.model.EntregaComProdutos
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand

/** What one NLP action is allowed to do. The ViewModel supplies the host and does not branch on the action. */
interface NlpCommandHost {
    fun entregas(): List<EntregaComProdutos>
    fun aplicarBusca(termo: String)
    suspend fun concluir(id: String): Boolean
    suspend fun conferir(itemId: String, conferido: Boolean)
    suspend fun snackbar(mensagem: String)
    fun logResultado(action: String, success: Boolean)
}

interface NlpActionHandler {
    val action: NlpAction
    suspend fun handle(command: NlpCommand, host: NlpCommandHost)
}

class NlpCommandRouter(
    handlers: List<NlpActionHandler>
) {
    private val porAcao = handlers.associateBy { it.action }

    suspend fun despachar(command: NlpCommand, host: NlpCommandHost) {
        val handler = porAcao[command.action] ?: porAcao.getValue(NlpAction.UNKNOWN)
        handler.handle(command, host)
    }
}

class SetSearchQueryHandler : NlpActionHandler {
    override val action = NlpAction.SET_SEARCH_QUERY
    override suspend fun handle(command: NlpCommand, host: NlpCommandHost) {
        command.searchTerm?.let(host::aplicarBusca)
        host.logResultado(AnalyticsEvents.ACTION_SET_SEARCH_QUERY, success = true)
    }
}

class ConcludeDeliveryHandler : NlpActionHandler {
    override val action = NlpAction.CONCLUDE_DELIVERY
    override suspend fun handle(command: NlpCommand, host: NlpCommandHost) {
        val entrega = host.entregas().firstOrNull {
            it.entrega.cliente.equals(command.targetClient, ignoreCase = true)
        }
        if (entrega == null) {
            host.snackbar("Cliente '${command.targetClient}' não encontrado na lista.")
            host.logResultado(AnalyticsEvents.ACTION_CONCLUDE_DELIVERY, success = false)
            return
        }
        val concluida = host.concluir(entrega.entrega.id)
        host.logResultado(AnalyticsEvents.ACTION_CONCLUDE_DELIVERY, success = concluida)
    }
}

class ConferirItemHandler : NlpActionHandler {
    override val action = NlpAction.CONFERIR_ITEM
    override suspend fun handle(command: NlpCommand, host: NlpCommandHost) {
        val targetItem = command.targetItem
        val estado = command.itemConferidoState ?: true
        if (targetItem.isNullOrBlank()) {
            host.snackbar("Não consegui identificar o produto a conferir.")
            return
        }
        val todas = host.entregas()
        if (command.targetClient != null) {
            val entrega = todas.firstOrNull {
                it.entrega.cliente.contains(command.targetClient, ignoreCase = true)
            }
            if (entrega == null) {
                host.snackbar("Cliente '${command.targetClient}' não encontrado.")
                return
            }
            val item = entrega.itens.firstOrNull { it.nome.contains(targetItem, ignoreCase = true) }
            if (item != null) {
                host.conferir(item.id, estado)
                val acao = if (estado) "conferido" else "desmarcado"
                host.snackbar("'${item.nome}' $acao.")
            } else {
                host.snackbar("Item '$targetItem' não encontrado para ${command.targetClient}.")
            }
        } else {
            val correspondentes = todas.flatMap { it.itens }
                .filter { it.nome.contains(targetItem, ignoreCase = true) }
            when (correspondentes.size) {
                0 -> host.snackbar("Nenhum item com '$targetItem' foi encontrado.")
                1 -> {
                    val item = correspondentes.first()
                    host.conferir(item.id, estado)
                    val acao = if (estado) "conferido" else "desmarcado"
                    host.snackbar("'${item.nome}' $acao.")
                }
                else -> host.snackbar(
                    "Existe mais de um item correspondente a '$targetItem'. " +
                        "Por favor, informe o nome do cliente."
                )
            }
        }
        host.logResultado(AnalyticsEvents.ACTION_CONFERIR_ITEM, success = true)
    }
}

class UnknownCommandHandler : NlpActionHandler {
    override val action = NlpAction.UNKNOWN
    override suspend fun handle(command: NlpCommand, host: NlpCommandHost) {
        val msg = if (command.isError) {
            "Erro de conexão com o serviço de IA. Verifique a rede."
        } else {
            "Não entendi o comando. Tente reformular."
        }
        host.snackbar(msg)
        host.logResultado(AnalyticsEvents.ACTION_UNKNOWN, success = false)
    }
}
