package br.com.ccortez.deliveryofflinefirst

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * Instrumented tests that demonstrate two distinct state-survival mechanisms in EntregasScreen:
 *
 *  • [campoNotLivedInViewModel_mantemValorAposRotacao]
 *    notLivedInViewModel uses rememberSaveable { mutableStateOf("") } in the Composable.
 *    The value is written to the Activity's Bundle (just like onSaveInstanceState), so it
 *    survives rotation without needing a ViewModel.
 *
 *  • [snackbar_naoReapareceAposRotacao]
 *    The snackbar is triggered by MutableSharedFlow (replay = 0) collected inside
 *    LaunchedEffect(Unit). SharedFlow does not cache its last emission, so after the
 *    Activity is recreated the LaunchedEffect restarts but finds no pending event —
 *    the snackbar stays hidden.
 *
 * Rotation is simulated with activityRule.scenario.recreate(), which destroys and
 * recreates the Activity exactly as the system does when the device is rotated.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RememberSaveableESnackbarTest {

    // Order matters: Hilt must be initialised before Compose creates the Activity
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Types text in the notLivedInViewModel field, rotates the screen, and verifies the text persists.
     *
     * Why it works: rememberSaveable serialises the String into the Activity's Bundle on every
     * recomposition. When the Activity is recreated (rotation), Compose reads the Bundle and
     * restores the saved value before the first frame — no ViewModel involved.
     *
     * Contrast with remember { mutableStateOf() }, which only survives recomposition and is
     * re-initialised from its default when the Activity is recreated.
     */
    @Test
    fun campoNotLivedInViewModel_mantemValorAposRotacao() {
        composeTestRule.onNodeWithTag("campo_not_lived_in_viewmodel")
            .performTextInput("Teste rememberSaveable")

        composeTestRule.waitForIdle()

        // Confirms the text was typed before rotating
        composeTestRule.onNodeWithTag("campo_not_lived_in_viewmodel")
            .assertTextContains("Teste rememberSaveable")

        // Simulate screen rotation
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // EXPECTED: text is still there — rememberSaveable restored it from the Bundle
        composeTestRule.onNodeWithTag("campo_not_lived_in_viewmodel")
            .assertTextContains("Teste rememberSaveable")
    }

    /**
     * Concludes a delivery to trigger the snackbar, then rotates the screen and
     * verifies the snackbar does NOT reappear.
     *
     * Why it works: the snackbar is driven by MutableSharedFlow(replay = 0).
     * SharedFlow emits the event exactly once; there is no cached value for new collectors.
     * After recreation, LaunchedEffect(Unit) restarts and subscribes to the Flow again,
     * but the event was already consumed — the Flow is silent, no snackbar is shown.
     *
     * If the event were stored in a StateFlow instead, the last value would replay
     * immediately to the new collector and the snackbar would reappear on every rotation —
     * the classic "snackbar loop" bug.
     */
    @Test
    fun snackbar_naoReapareceAposRotacao() {
        // Wait for seed data to be loaded from Room before interacting with the list
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Concluir")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Click the first visible "Concluir" button to emit the snackbar event via SharedFlow
        composeTestRule.onAllNodesWithText("Concluir")[0].performClick()

        // Wait for the snackbar to appear, confirming the event was emitted and collected
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule
                .onAllNodesWithText("Entrega concluída. Sincronizará quando houver rede.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Simulate screen rotation
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // EXPECTED: snackbar is absent — SharedFlow(replay=0) did not replay the event
        // to the LaunchedEffect that restarted after recreation
        composeTestRule.onNodeWithText("Entrega concluída. Sincronizará quando houver rede.")
            .assertDoesNotExist()
    }
}
