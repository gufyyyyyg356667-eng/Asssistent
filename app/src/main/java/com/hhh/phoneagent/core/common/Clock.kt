package com.hhh.phoneagent.core.common

import javax.inject.Inject

/** Injectable time source so time-dependent logic can be tested deterministically. */
fun interface Clock {
    fun nowMillis(): Long
}

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
