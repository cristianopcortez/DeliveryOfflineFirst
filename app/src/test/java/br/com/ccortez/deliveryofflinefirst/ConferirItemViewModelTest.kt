package br.com.ccortez.deliveryofflinefirst

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpAction
import br.com.ccortez.deliveryofflinefirst.domain.nlp.NlpCommand
import br.com.ccortez.deliveryofflinefirst.fake.FakeAnalyticsRepositoryForTest
import br.com.ccortez.deliveryofflinefirst.fake.FakeEntregaRepositoryForTest
import br.com.ccortez.deliveryofflinefirst.fake.FakeNlpRepositoryForTest
import br.com.ccortez.deliveryofflinefirst.fake.FakeRemoteConfigRepositoryForTest
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasEvent
import br.com.ccortez.deliveryofflinefirst.presentation.viewmodel.EntregasViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the `CONFERIR_ITEM` branch of [EntregasViewModel.processarComandoNLP].
 *
 * ## Why Robolectric
 * [EntregasViewModel] constructs a [WorkManager] StateFlow inside its `init` block via
 * `WorkManager.getInstance(context)`. Pure JVM tests have no Android context, so Robolectric
 * is used to provide a real `Context` via [ApplicationProvider.getApplicationContext] and
 * [WorkManagerTestInitHelper.initializeTestWorkManager] sets up WorkManager without a device.
 *
 * ## Test isolation strategy
 * All Firebase dependencies (Gemini, Remote Config, Analytics) are replaced by the
 * `*ForTest` fakes in `src/test/.../fake/`. The [FakeEntregaRepositoryForTest] is
 * pre-loaded with the same seed data as the production `EntregasViewModel`, so that
 * `_uiState.value.entregas` is populated before `processarComandoNLP()` reads it.
 *
 * ## Coroutine strategy
 * [StandardTestDispatcher] is set as `Dispatchers.Main` before each test. After
 * constructing the ViewModel, [advanceUntilIdle] drains all coroutines launched in
 * `init` (seed guard, `observarEntregas`, `fetchRemoteConfig`) so that
 * `_uiState.value.entregas` is fully populated before any NLP assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConferirItemViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeRepo: FakeEntregaRepositoryForTest
    private lateinit var fakeNlp: FakeNlpRepositoryForTest
    private lateinit var viewModel: EntregasViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        fakeRepo = FakeEntregaRepositoryForTest()
        fakeNlp = FakeNlpRepositoryForTest()

        viewModel = EntregasViewModel(
            repository          = fakeRepo,
            nlpRepository       = fakeNlp,
            remoteConfigRepository = FakeRemoteConfigRepositoryForTest(),
            analyticsRepository = FakeAnalyticsRepositoryForTest(),
            context             = context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Collects all [EntregasEvent] emissions during the block execution and returns them.
     *
     * A collection coroutine is launched in [testScope] before the block runs, ensuring
     * no events are missed. [advanceUntilIdle] drains all pending coroutines — including
     * `processarComandoNLP` and its NLP + repository calls — before we inspect the list.
     */
    private fun coletarEventos(block: () -> Unit): List<EntregasEvent> =
        testScope.run {
            val capturados = mutableListOf<EntregasEvent>()
            val job = launch { viewModel.eventos.collect { capturados.add(it) } }
            // Drain init coroutines so _uiState.entregas is populated
            testScope.advanceUntilIdle()
            block()
            testScope.advanceUntilIdle()
            job.cancel()
            capturados
        }

    // ── Cenário A — Sucesso Global Sem Ambiguidade ────────────────────────────

    /**
     * "Mochila Escolar Estampada" (seed-1-b) exists only in Ana Paula's delivery.
     * The global search finds exactly one match → the item is checked and a success
     * Snackbar is emitted. No other items are written to the repository.
     *
     * This validates the `correspondentes.size == 1` branch in `processarComandoNLP`.
     */
    @Test
    fun cenarioA_globalSemAmbiguidade_confereItemUnico() = runTest(testDispatcher) {
        fakeNlp.response = NlpCommand(
            action             = NlpAction.CONFERIR_ITEM,
            targetItem         = "mochila",
            itemConferidoState = true
        )

        val eventos = coletarEventos { viewModel.processarComandoNLP("conferei a mochila") }

        assertEquals(
            "Deve escrever exatamente uma entrada — seed-1-b conferido=true",
            listOf("seed-1-b" to true),
            fakeRepo.conferidosRegistrados
        )

        val snackbar = eventos.filterIsInstance<EntregasEvent.ShowSnackbar>().last()
        assertTrue(
            "Snackbar deve confirmar o nome exato do item",
            snackbar.message.contains("Mochila Escolar Estampada", ignoreCase = true)
        )
        assertTrue(
            "Snackbar deve indicar que o item foi conferido",
            snackbar.message.contains("conferido", ignoreCase = true)
        )
    }

    // ── Cenário B — Fallback de Ambiguidade Protetiva ─────────────────────────

    /**
     * "Caixa de Papelão 50×40×30 cm" exists in **two** deliveries:
     *   - seed-1-d (Ana Paula Ferreira)
     *   - seed-2-f (Carlos Lima)
     *
     * The global search finds 2 matches → the ambiguity guard fires. The repository
     * must NOT be called (no write to Room), and the Snackbar must instruct the driver
     * to specify the client name before proceeding.
     *
     * This validates the `correspondentes.size > 1` branch.
     */
    @Test
    fun cenarioB_ambiguidadeProtetiva_naoEscreveNoBancoEAlertaMotorista() = runTest(testDispatcher) {
        fakeNlp.response = NlpCommand(
            action             = NlpAction.CONFERIR_ITEM,
            targetItem         = "caixa de papelão",
            itemConferidoState = true
            // targetClient omitido — busca global
        )

        val eventos = coletarEventos { viewModel.processarComandoNLP("conferei a caixa de papelão") }

        assertTrue(
            "Nenhuma escrita deve ocorrer no banco quando a busca é ambígua",
            fakeRepo.conferidosRegistrados.isEmpty()
        )

        val snackbar = eventos.filterIsInstance<EntregasEvent.ShowSnackbar>().last()
        assertTrue(
            "Snackbar deve mencionar o termo ambíguo",
            snackbar.message.contains("caixa de papelão", ignoreCase = true)
        )
        assertTrue(
            "Snackbar deve solicitar o nome do cliente para desambiguação",
            snackbar.message.contains("nome do cliente", ignoreCase = true)
        )
    }

    // ── Cenário C — Resolução de Ambiguidade por Contexto ─────────────────────

    /**
     * Same ambiguous term "caixa de papelão", but now with `targetClient = "Ana Paula"`.
     * The search is scoped to Ana Paula's delivery → only seed-1-d matches.
     * The item is checked; no ambiguity warning is emitted.
     *
     * This validates the `targetClient != null` branch: the guard never fires because
     * the match is restricted to a single client's item list before counting.
     */
    @Test
    fun cenarioC_resolucaoContextual_confereItemNoClienteEspecifico() = runTest(testDispatcher) {
        fakeNlp.response = NlpCommand(
            action             = NlpAction.CONFERIR_ITEM,
            targetItem         = "caixa de papelão",
            targetClient       = "Ana Paula",
            itemConferidoState = true
        )

        val eventos = coletarEventos {
            viewModel.processarComandoNLP("conferei a caixa de papelão da Ana Paula")
        }

        assertEquals(
            "Deve escrever exatamente uma entrada — seed-1-d da Ana Paula conferido=true",
            listOf("seed-1-d" to true),
            fakeRepo.conferidosRegistrados
        )

        val snackbar = eventos.filterIsInstance<EntregasEvent.ShowSnackbar>().last()
        assertTrue(
            "Snackbar deve confirmar o nome exato do item",
            snackbar.message.contains("Caixa de Papelão 50×40×30 cm", ignoreCase = true)
        )
        assertTrue(
            "Snackbar deve indicar que o item foi conferido",
            snackbar.message.contains("conferido", ignoreCase = true)
        )
    }
}
