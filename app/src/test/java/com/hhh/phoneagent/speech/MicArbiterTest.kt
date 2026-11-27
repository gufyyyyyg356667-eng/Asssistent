package com.hhh.phoneagent.speech

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicArbiterTest {

    @Test
    fun claimIsImmediateWhenWakeHardwareIsFree() = runTest {
        val arbiter = MicArbiter()
        assertTrue(arbiter.claim(MicOwner.WAKE_WORD))
        assertTrue(arbiter.claim(MicOwner.SPEECH_TURN))
        assertEquals(MicOwner.SPEECH_TURN, arbiter.owner.value)
    }

    @Test
    fun turnWaitsUntilWakeClosesAudioRecord() = runTest {
        val arbiter = MicArbiter()
        arbiter.claim(MicOwner.WAKE_WORD)
        arbiter.setWakeHardwareHeld(true)

        val pending = async { arbiter.claim(MicOwner.SPEECH_TURN) }
        runCurrent()
        assertFalse(pending.isCompleted)

        arbiter.setWakeHardwareHeld(false)
        assertTrue(pending.await())
        assertEquals(MicOwner.SPEECH_TURN, arbiter.owner.value)
    }

    @Test
    fun failedHandoffFreesTheMic() = runTest {
        val arbiter = MicArbiter()
        arbiter.claim(MicOwner.WAKE_WORD)
        arbiter.setWakeHardwareHeld(true)

        assertFalse(arbiter.claim(MicOwner.SPEECH_TURN))
        assertEquals(MicOwner.NONE, arbiter.owner.value)
    }

    @Test
    fun releaseOnlyClearsOwnRole() {
        val arbiter = MicArbiter()
        arbiter.release(MicOwner.WAKE_WORD)
        kotlinx.coroutines.runBlocking { arbiter.claim(MicOwner.SPEECH_TURN) }
        arbiter.release(MicOwner.WAKE_WORD)
        assertEquals(MicOwner.SPEECH_TURN, arbiter.owner.value)
    }
}
