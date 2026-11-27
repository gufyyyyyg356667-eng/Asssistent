package com.hhh.phoneagent.data.remote.gemini

import com.hhh.phoneagent.core.common.Clock
import com.hhh.phoneagent.domain.repository.GeminiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyRotatorTest {

    private class FakeStore(var keys: List<String>) : GeminiKeyStore {
        override fun keys(): List<String> = keys
        override fun saveKeys(keys: List<String>) {
            this.keys = keys
        }
    }

    private var now = 0L
    private val clock = Clock { now }

    @Test
    fun roundRobinAcrossKeys() {
        val rotator = ApiKeyRotator(FakeStore(listOf("a", "b", "c")), clock)
        val seen = List(4) { rotator.acquire()!!.value }
        assertEquals(listOf("a", "b", "c", "a"), seen)
    }

    @Test
    fun rateLimitedKeyCoolsDownUntilExpiry() {
        val rotator = ApiKeyRotator(FakeStore(listOf("a", "b")), clock)
        val first = rotator.acquire()!!
        assertEquals("a", first.value)
        rotator.reportRateLimited(first, retryAfterMillis = 1_000L)

        assertEquals("b", rotator.acquire()!!.value)
        assertEquals("b", rotator.acquire()!!.value)

        now = 1_000L
        assertEquals("a", rotator.acquire()!!.value)
    }

    @Test
    fun rejectedKeyIsRemoved() {
        val rotator = ApiKeyRotator(FakeStore(listOf("a", "b")), clock)
        rotator.reportRejected(rotator.acquire()!!)
        assertEquals(1, rotator.usableKeyCount())
        assertEquals("b", rotator.acquire()!!.value)
        assertEquals("b", rotator.acquire()!!.value)
    }

    @Test
    fun returnsNullWhenNoKeysAvailable() {
        val rotator = ApiKeyRotator(FakeStore(emptyList()), clock)
        assertNull(rotator.acquire())
    }

    @Test
    fun maskedKeyHidesAllButLastFour() {
        assertEquals("…wxyz", ApiKeyLease("abcdefgwxyz").masked)
    }
}
