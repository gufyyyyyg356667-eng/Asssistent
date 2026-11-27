package com.hhh.phoneagent.service.wakeword.audio

import com.hhh.phoneagent.service.wakeword.WakeWordConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class LogMelFrontendTest {

    private val config = WakeWordConfig()

    @Test
    fun producesFramesWithConfiguredMelSizeAndKeepsCarryAcrossCalls() {
        val frontend = LogMelFrontend(config)
        val first = frontend.push(ShortArray(1_600) { 1_000 }, 1_600)
        assertEquals(8, first.size)
        assertEquals(config.melBins, first[0].size)

        val second = frontend.push(ShortArray(160), 160)
        assertEquals(1, second.size)
    }
}
