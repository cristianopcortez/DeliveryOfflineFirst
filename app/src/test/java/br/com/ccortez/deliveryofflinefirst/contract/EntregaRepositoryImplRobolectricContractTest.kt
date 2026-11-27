package br.com.ccortez.deliveryofflinefirst.contract

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [EntregaRepositoryContractTest] run against the real, Room-backed `EntregaRepositoryImpl`
 * from the unit-test tree. Robolectric supplies the [android.content.Context] Room needs for
 * an in-memory database — all arrangement/assertion logic is inherited unmodified from
 * [RoomEntregaRepositoryContractTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntregaRepositoryImplRobolectricContractTest : RoomEntregaRepositoryContractTest()
