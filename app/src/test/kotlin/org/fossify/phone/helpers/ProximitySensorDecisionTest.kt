package org.fossify.phone.helpers

import android.telecom.Call
import android.telecom.CallAudioState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximitySensorDecisionTest {
    @Test
    fun `used during calls on the earpiece`() {
        for (state in listOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE, Call.STATE_HOLDING)) {
            assertTrue(shouldUseProximitySensor(false, listOf(state), CallAudioState.ROUTE_EARPIECE))
        }
    }

    @Test
    fun `not used while ringing so the call can be answered`() {
        assertFalse(shouldUseProximitySensor(false, listOf(Call.STATE_RINGING), CallAudioState.ROUTE_EARPIECE))
    }

    @Test
    fun `kept on at the ear when a second call is waiting`() {
        val callWaiting = listOf(Call.STATE_ACTIVE, Call.STATE_RINGING)
        assertTrue(shouldUseProximitySensor(false, callWaiting, CallAudioState.ROUTE_EARPIECE))
        assertTrue(shouldUseProximitySensor(false, callWaiting.reversed(), CallAudioState.ROUTE_EARPIECE))
    }

    @Test
    fun `not used on speaker`() {
        assertFalse(shouldUseProximitySensor(false, listOf(Call.STATE_ACTIVE), CallAudioState.ROUTE_SPEAKER))
    }

    @Test
    fun `not used when disabled in settings`() {
        assertFalse(shouldUseProximitySensor(true, listOf(Call.STATE_ACTIVE), CallAudioState.ROUTE_EARPIECE))
    }

    @Test
    fun `released when there is no call or it ended`() {
        assertFalse(shouldUseProximitySensor(false, emptyList(), CallAudioState.ROUTE_EARPIECE))
        assertFalse(shouldUseProximitySensor(false, listOf(Call.STATE_DISCONNECTED), CallAudioState.ROUTE_EARPIECE))
    }

    @Test
    fun `still used when the audio state is not known yet`() {
        assertTrue(shouldUseProximitySensor(false, listOf(Call.STATE_ACTIVE), null))
    }
}
