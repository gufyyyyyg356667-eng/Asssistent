package com.hhh.phoneagent.domain.workflow

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SubstituteTest {

    @Test
    fun replacesKnownVariablesAndKeepsUnknownOnes() {
        val args = JsonObject(mapOf("text" to JsonPrimitive("Hi {{name}}, {{missing}}")))
        val out = substitute(args, mapOf("name" to "Rima"))
        assertEquals("Hi Rima, {{missing}}", out["text"]!!.jsonPrimitive.content)
    }
}
