package com.hhh.phoneagent.domain.security

import com.hhh.phoneagent.domain.model.RiskLevel

data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val risk: RiskLevel,
    val summary: String,
    val requestedAtMillis: Long,
)

enum class ApprovalDecision { APPROVED, DENIED, TIMED_OUT }

/** Suspends the agent until the user decides. Implementations must fail closed (deny on timeout). */
interface ApprovalGate {
    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision
}
