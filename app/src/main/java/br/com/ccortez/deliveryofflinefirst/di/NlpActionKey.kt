package br.com.ccortez.deliveryofflinefirst.di

import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import dagger.MapKey

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class NlpActionKey(val value: NlpAction)
