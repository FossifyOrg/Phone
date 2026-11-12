package org.fossify.phone.helpers

import android.telecom.Call
import android.telecom.CallAudioState
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothRouteDecisionTest {
    private val earpieceAndBluetooth =
        CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER or CallAudioState.ROUTE_BLUETOOTH
    private val earpieceOnly = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER

    @Test
    fun `switches an active call from earpiece to a connected car or headset`() {
        assertEquals(
            BluetoothRouteDecision.SWITCH_TO_BLUETOOTH,
            bluetoothRouteDecision(Call.STATE_ACTIVE, earpieceAndBluetooth, CallAudioState.ROUTE_EARPIECE)
        )
    }

    @Test
    fun `switches outgoing calls while dialing and connecting`() {
        for (state in listOf(Call.STATE_DIALING, Call.STATE_CONNECTING)) {
            assertEquals(
                BluetoothRouteDecision.SWITCH_TO_BLUETOOTH,
                bluetoothRouteDecision(state, earpieceAndBluetooth, CallAudioState.ROUTE_EARPIECE)
            )
        }
    }

    @Test
    fun `leaves ringing calls alone`() {
        assertEquals(
            BluetoothRouteDecision.WAIT,
            bluetoothRouteDecision(Call.STATE_RINGING, earpieceAndBluetooth, CallAudioState.ROUTE_EARPIECE)
        )
    }

    @Test
    fun `waits while no bluetooth route is available yet`() {
        assertEquals(
            BluetoothRouteDecision.WAIT,
            bluetoothRouteDecision(Call.STATE_ACTIVE, earpieceOnly, CallAudioState.ROUTE_EARPIECE)
        )
    }

    @Test
    fun `never overrides a route the user or system already chose`() {
        val allRoutes = earpieceAndBluetooth or CallAudioState.ROUTE_WIRED_HEADSET
        val chosenRoutes = listOf(
            CallAudioState.ROUTE_SPEAKER,
            CallAudioState.ROUTE_WIRED_HEADSET,
            CallAudioState.ROUTE_BLUETOOTH
        )
        for (route in chosenRoutes) {
            assertEquals(
                BluetoothRouteDecision.KEEP_CURRENT,
                bluetoothRouteDecision(Call.STATE_ACTIVE, allRoutes, route)
            )
        }
    }

    @Test
    fun `does nothing for ended calls`() {
        for (state in listOf(Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED)) {
            assertEquals(
                BluetoothRouteDecision.WAIT,
                bluetoothRouteDecision(state, earpieceAndBluetooth, CallAudioState.ROUTE_EARPIECE)
            )
        }
    }
}
