package org.fossify.phone.helpers

import android.telecom.Call
import android.telecom.CallAudioState

enum class BluetoothRouteDecision {
    /** Not the right moment (ringing / no BT route yet) — check again later. */
    WAIT,

    /** A deliberate route is already in use — never override it during this call. */
    KEEP_CURRENT,

    /** Move call audio to the connected headset / car. */
    SWITCH_TO_BLUETOOTH
}

/**
 * Pure decision logic for [CallManager.preferBluetoothRouteIfAvailable], kept free of Android
 * runtime objects so it can be unit tested. Only uses compile-time constants.
 *
 * Ringing calls are left alone: Telecom handles in-band ringing itself and requesting a route
 * before the call is answered/placed can be ignored or cause a glitch on some devices.
 */
fun bluetoothRouteDecision(callState: Int, supportedRouteMask: Int, currentRoute: Int): BluetoothRouteDecision {
    val isOffHook = callState == Call.STATE_ACTIVE ||
        callState == Call.STATE_DIALING ||
        callState == Call.STATE_CONNECTING
    if (!isOffHook) {
        return BluetoothRouteDecision.WAIT
    }

    if (supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH == 0) {
        return BluetoothRouteDecision.WAIT
    }

    // Only the earpiece counts as "nobody chose anything"; speaker / wired headset / BT are kept.
    if (currentRoute != CallAudioState.ROUTE_EARPIECE) {
        return BluetoothRouteDecision.KEEP_CURRENT
    }

    return BluetoothRouteDecision.SWITCH_TO_BLUETOOTH
}
