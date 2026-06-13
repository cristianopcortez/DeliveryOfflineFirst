package br.com.ccortez.deliveryofflinefirst.di

import android.content.Context
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.repository.EntregaRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    fun provideEntregaDao(db: AppDatabase): EntregaDao =
        db.entregaDao()

    @Provides
    @Singleton
    fun provideEntregaRepository(dao: EntregaDao): EntregaRepository =
        EntregaRepositoryImpl(dao)
}
