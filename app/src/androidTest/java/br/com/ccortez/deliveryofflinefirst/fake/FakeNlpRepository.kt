package br.com.ccortez.deliveryofflinefirst.fake

import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand
import br.com.ccortez.deliveryofflinefirst.domain.repository.NlpRepository

/**
 * Test double for [NlpRepository].
 *
 * Returns a configurable [NlpCommand] immediately, with no network call or API key required.
 * Default is [NlpAction.UNKNOWN] — a safe fallback that triggers a snackbar without crashing
 * and keeps tests that don't care about NLP outcomes simple.
 *
 * Override [response] to test specific NLP command paths (SET_SEARCH_QUERY, CONCLUDE_DELIVERY).
 */
class FakeNlpRepository(
    var response: NlpCommand = NlpCommand(action = NlpAction.UNKNOWN)
) : NlpRepository {
    override suspend fun interpretarComando(comando: String): NlpCommand = response
}
