package br.com.ccortez.deliveryofflinefirst.contract

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * [EntregaRepositoryContractTest] run against the real, Room-backed `EntregaRepositoryImpl` on
 * an actual device/emulator. All arrangement/assertion logic is inherited unmodified from
 * [RoomEntregaRepositoryContractTest]; only the instrumentation runner differs from the
 * Robolectric-backed unit-test sibling.
 */
@RunWith(AndroidJUnit4::class)
class EntregaRepositoryImplContractTest : RoomEntregaRepositoryContractTest()
