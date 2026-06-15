package br.com.ccortez.deliveryofflinefirst.di

import android.content.Context
import androidx.datastore.core.DataStore
import br.com.ccortez.deliveryofflinefirst.data.local.AppDatabase
import br.com.ccortez.deliveryofflinefirst.data.local.EntregaDao
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.SettingsConfig
import br.com.ccortez.deliveryofflinefirst.data.local.datastore.settingsDataStore
import br.com.ccortez.deliveryofflinefirst.data.repository.EntregaRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.data.repository.SettingsRepositoryImpl
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import br.com.ccortez.deliveryofflinefirst.domain.repository.SettingsRepository
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

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<SettingsConfig> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<SettingsConfig>): SettingsRepository =
        SettingsRepositoryImpl(dataStore)
}
