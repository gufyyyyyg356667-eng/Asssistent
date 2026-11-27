package com.hhh.phoneagent.data.remote.gemini

import com.hhh.phoneagent.domain.tool.objectSchema
import com.hhh.phoneagent.domain.tool.stringParam
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiMapperTest {

    @Test
    fun schemaTypesBecomeUpperCaseRecursively() {
        val schema = objectSchema(
            properties = mapOf("ref" to stringParam("ref")),
            required = listOf("ref"),
        )
        val converted = schema.toGeminiSchema()

        assertEquals("OBJECT", converted["type"]!!.jsonPrimitive.content)
        assertEquals(
            "STRING",
            converted["properties"]!!.jsonObject["ref"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
    }
}
