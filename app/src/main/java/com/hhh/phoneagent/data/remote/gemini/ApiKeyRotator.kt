package com.hhh.phoneagent.data.remote.gemini

import com.hhh.phoneagent.core.common.Clock
import com.hhh.phoneagent.domain.repository.GeminiKeyStore
import javax.inject.Inject
import javax.inject.Singleton

/** A key checked out for one request. Only the masked form should ever be logged. */
data class ApiKeyLease(val value: String) {
    val masked: String get() = "…" + value.takeLast(4)
}

/**
 * Round-robin over the stored keys. A key that is rate limited cools down, and a key that is
 * rejected (401/403) is dropped for this process lifetime. When every key is unavailable,
 * [acquire] returns null.
 */
@Singleton
class ApiKeyRotator @Inject constructor(
    private val store: GeminiKeyStore,
    private val clock: Clock,
) {
    private val lock = Any()
    private var cursor = 0
    private val cooldownUntil = HashMap<String, Long>()
    private val rejected = HashSet<String>()

    fun acquire(): ApiKeyLease? {
        synchronized(lock) {
            val keys = store.keys().filter { it !in rejected }
            if (keys.isEmpty()) return null
            val now = clock.nowMillis()
            for (offset in keys.indices) {
                val index = (cursor + offset) % keys.size
                val key = keys[index]
                if ((cooldownUntil[key] ?: 0L) <= now) {
                    cursor = (index + 1) % keys.size
                    return ApiKeyLease(key)
                }
            }
            return null
        }
    }

    fun reportRateLimited(lease: ApiKeyLease, retryAfterMillis: Long?) {
        synchronized(lock) {
            val wait = maxOf(retryAfterMillis ?: 0L, DEFAULT_COOLDOWN_MILLIS)
            cooldownUntil[lease.value] = clock.nowMillis() + wait
        }
    }

    fun reportRejected(lease: ApiKeyLease) {
        synchronized(lock) { rejected += lease.value }
    }

    fun usableKeyCount(): Int = synchronized(lock) {
        store.keys().count { it !in rejected }
    }

    companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 30_000L
    }
}
