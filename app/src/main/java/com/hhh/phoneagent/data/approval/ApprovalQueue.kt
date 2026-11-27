package com.hhh.phoneagent.data.approval

import com.hhh.phoneagent.domain.security.ApprovalDecision
import com.hhh.phoneagent.domain.security.ApprovalGate
import com.hhh.phoneagent.domain.security.ApprovalRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One approval at a time. The overlay UI (Phase 4) observes [currentRequest] and calls
 * [resolve]. The whole wait, including time spent queued, is capped, and expiry means DENIED.
 */
@Singleton
class ApprovalQueue @Inject constructor() : ApprovalGate {

    private val mutex = Mutex()
    private val requestState = MutableStateFlow<ApprovalRequest?>(null)

    @Volatile
    private var waiter: Waiter? = null

    val currentRequest: StateFlow<ApprovalRequest?> = requestState.asStateFlow()

    override suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision =
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            mutex.withLock { ask(request) }
        } ?: ApprovalDecision.TIMED_OUT

    /** Returns true if [requestId] matched the pending request and the decision was accepted. */
    fun resolve(requestId: String, decision: ApprovalDecision): Boolean {
        val current = waiter ?: return false
        return current.id == requestId && current.deferred.complete(decision)
    }

    private suspend fun ask(request: ApprovalRequest): ApprovalDecision {
        val deferred = CompletableDeferred<ApprovalDecision>()
        waiter = Waiter(request.id, deferred)
        requestState.value = request
        try {
            return deferred.await()
        } finally {
            waiter = null
            requestState.value = null
        }
    }

    private class Waiter(val id: String, val deferred: CompletableDeferred<ApprovalDecision>)

    companion object {
        const val TIMEOUT_MILLIS = 60_000L
    }
}
