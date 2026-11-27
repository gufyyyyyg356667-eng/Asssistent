package com.hhh.phoneagent.domain.security

import com.hhh.phoneagent.domain.model.RiskLevel

/**
 * Escalates risk from the target's visible label. A tap on "Send" or "Pay" is treated as
 * HIGH_RISK even when the tool's base risk is lower.
 */
object RiskPolicy {

    private val sensitiveLabel = Regex(
        "\\b(send|pay|payment|purchase|buy|order|checkout|transfer|delete|remove|uninstall|" +
            "reset|format|wipe|erase|confirm|submit|log ?out|sign ?out)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun effectiveRisk(base: RiskLevel, targetLabel: String?): RiskLevel = when {
        base == RiskLevel.HIGH_RISK -> base
        !targetLabel.isNullOrBlank() && sensitiveLabel.containsMatchIn(targetLabel) -> RiskLevel.HIGH_RISK
        else -> base
    }

    fun requiresApproval(risk: RiskLevel): Boolean = risk == RiskLevel.HIGH_RISK
}
