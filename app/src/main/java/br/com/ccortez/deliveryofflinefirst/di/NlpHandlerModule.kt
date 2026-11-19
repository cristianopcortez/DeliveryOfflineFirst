package br.com.ccortez.deliveryofflinefirst.di

import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.ConcludeDeliveryHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.ConferirItemHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.NlpActionHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.SetSearchQueryHandler
import br.com.ccortez.deliveryofflinefirst.presentation.nlp.UnknownCommandHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * Lives outside [AppModule] because instrumented tests uninstall that module.
 * A new NLP action is a handler class plus one binding here. The ViewModel receives the map.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NlpHandlerModule {

    @Binds
    @IntoMap
    @NlpActionKey(NlpAction.SET_SEARCH_QUERY)
    abstract fun bindSetSearchQuery(impl: SetSearchQueryHandler): NlpActionHandler

    @Binds
    @IntoMap
    @NlpActionKey(NlpAction.CONCLUDE_DELIVERY)
    abstract fun bindConcludeDelivery(impl: ConcludeDeliveryHandler): NlpActionHandler

    @Binds
    @IntoMap
    @NlpActionKey(NlpAction.CONFERIR_ITEM)
    abstract fun bindConferirItem(impl: ConferirItemHandler): NlpActionHandler

    @Binds
    @IntoMap
    @NlpActionKey(NlpAction.UNKNOWN)
    abstract fun bindUnknown(impl: UnknownCommandHandler): NlpActionHandler
}
