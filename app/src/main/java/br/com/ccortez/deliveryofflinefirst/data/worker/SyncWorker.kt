package br.com.ccortez.deliveryofflinefirst.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.ccortez.deliveryofflinefirst.domain.repository.EntregaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlin.random.Random

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: EntregaRepository
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            delay(2_000) // simula latência de rede

            if (Random.nextFloat() < 0.3f) {
                // 30% de falha aleatória — WorkManager reagenda com backoff exponencial
                Result.retry()
            } else {
                repository.marcarTodasSincronizadas()
                Result.success()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
