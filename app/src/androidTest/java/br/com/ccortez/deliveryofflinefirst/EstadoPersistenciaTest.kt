package br.com.ccortez.deliveryofflinefirst

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests that demonstrate the lifecycle difference between
 * the two filter states in EntregasScreen:
 *
 *  • [campoBusca_mantemValorAposRotacao]
 *    searchQuery lives in the ViewModel as MutableStateFlow → survives rotation.
 *
 *  • [dropdown_resetaParaTodosAposRotacao]
 *    selectedCliente uses remember { mutableStateOf } in the Composable → it is ephemeral,
 *    resets to "Todos" when the Activity is recreated (screen rotation).
 *
 * Rotation is simulated with activityRule.scenario.recreate(), which destroys and
 * recreates the Activity exactly as the system does when the device is rotated.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EstadoPersistenciaTest {

    // Order matters: Hilt must be initialised before Compose creates the Activity
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        // Injects @Inject fields declared in this test class (none here,
        // but keeping it is good practice for when the test grows)
        hiltRule.inject()
    }

    /**
     * Types "Ana" in the search field, rotates the screen, and verifies the text persists.
     *
     * Why it works: searchQuery is a MutableStateFlow in the ViewModel.
     * The ViewModel survives Activity recreation — the Flow keeps emitting
     * the last value, and the Composable re-collects it via collectAsStateWithLifecycle.
     */
    @Test
    fun campoBusca_mantemValorAposRotacao() {
        composeTestRule.onNodeWithTag("campo_busca")
            .performTextInput("Ana")

        composeTestRule.waitForIdle()

        // Confirms the text was typed before rotating
        composeTestRule.onNodeWithTag("campo_busca")
            .assertTextContains("Ana")

        // Simulate screen rotation
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // EXPECTED: "Ana" is still in the field — the ViewModel was not destroyed
        composeTestRule.onNodeWithTag("campo_busca")
            .assertTextContains("Ana")
    }

    /**
     * Selects "Ana Paula" in the dropdown, rotates the screen, and verifies it reset to "Todos".
     *
     * Why it works: selectedCliente uses remember { mutableStateOf("Todos") }.
     * When the Activity is recreated, the Composable is recomposed from scratch and remember
     * is re-initialised with its default value — the filter is intentionally lost.
     */
    @Test
    fun dropdown_resetaParaTodosAposRotacao() {
        // Wait for seed data to be loaded from Room
        // (EntregasViewModel.popularBancoSeVazio inserts "Ana Paula" among others)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Ana Paula")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Opens the dropdown by clicking the OutlinedTextField (tagged as "dropdown_cliente")
        composeTestRule.onNodeWithTag("dropdown_cliente")
            .performClick()

        composeTestRule.waitForIdle()

        // Click the menu item tagged "dropdown_item_Ana Paula" — avoids ambiguity with
        // the delivery card that also contains the text "Ana Paula" in the list below
        composeTestRule.onNodeWithTag("dropdown_item_Ana Paula")
            .performClick()

        composeTestRule.waitForIdle()

        // Confirms the dropdown shows "Ana Paula" before rotating
        composeTestRule.onNodeWithTag("dropdown_cliente")
            .assertTextContains("Ana Paula")

        // Simulate screen rotation
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // EXPECTED: dropdown reset to "Todos" — remember was re-initialised
        composeTestRule.onNodeWithTag("dropdown_cliente")
            .assertTextContains("Todos")
    }
}
