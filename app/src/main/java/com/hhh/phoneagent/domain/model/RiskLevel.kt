package com.hhh.phoneagent.domain.model

/** SAFE runs silently, MODERATE runs and is logged, HIGH_RISK waits for explicit approval. */
enum class RiskLevel { SAFE, MODERATE, HIGH_RISK }
