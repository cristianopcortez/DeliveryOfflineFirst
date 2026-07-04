package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository

/** Configurable test double — set [response] before each unit test. */
class FakeNlpRepositoryForTest(
    var response: NlpCommand = NlpCommand(action = NlpAction.UNKNOWN)
) : NlpRepository {
    override suspend fun interpretarComando(comando: String): NlpCommand = response
}
