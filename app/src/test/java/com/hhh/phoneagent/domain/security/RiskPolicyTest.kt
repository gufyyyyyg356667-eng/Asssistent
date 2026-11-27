package com.hhh.phoneagent.domain.security

import com.hhh.phoneagent.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskPolicyTest {

    @Test
    fun sensitiveLabelsEscalateToHighRisk() {
        listOf("Send", "Pay now", "Confirm purchase", "Delete account", "Log out").forEach { label ->
            assertEquals(label, RiskLevel.HIGH_RISK, RiskPolicy.effectiveRisk(RiskLevel.MODERATE, label))
        }
    }

    @Test
    fun wordBoundariesPreventFalsePositives() {
        assertEquals(RiskLevel.MODERATE, RiskPolicy.effectiveRisk(RiskLevel.MODERATE, "Display settings"))
        assertEquals(RiskLevel.MODERATE, RiskPolicy.effectiveRisk(RiskLevel.MODERATE, "Resend code"))
    }

    @Test
    fun ordinaryLabelsKeepBaseRisk() {
        assertEquals(RiskLevel.SAFE, RiskPolicy.effectiveRisk(RiskLevel.SAFE, "Settings"))
        assertEquals(RiskLevel.MODERATE, RiskPolicy.effectiveRisk(RiskLevel.MODERATE, null))
    }

    @Test
    fun highRiskBaseIsNeverLowered() {
        assertEquals(RiskLevel.HIGH_RISK, RiskPolicy.effectiveRisk(RiskLevel.HIGH_RISK, "Open"))
    }
}
