package com.hhh.phoneagent.domain.workflow

import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.data.workflow.WorkflowCodec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowCodecTest {

    private val codec = WorkflowCodec(Json { ignoreUnknownKeys = true; classDiscriminator = "type" })

    @Test
    fun parsesValidWorkflow() {
        val text = """
            {"name":"morning","steps":[
              {"type":"tool","tool":"global_action","args":{"action":"home"}},
              {"type":"wait","millis":500},
              {"type":"stop","message":"done"}
            ]}
        """.trimIndent()
        val result = codec.parse(text)
        assertTrue(result is AppResult.Success)
        assertEquals(3, (result as AppResult.Success).value.steps.size)
    }

    @Test
    fun rejectsNestedWorkflowCall() {
        val text = """{"name":"x","steps":[{"type":"tool","tool":"run_workflow"}]}"""
        assertTrue(codec.parse(text) is AppResult.Failure)
    }

    @Test
    fun rejectsEmptyAndOversizedWaits() {
        assertTrue(codec.parse("""{"name":"x","steps":[]}""") is AppResult.Failure)
        assertTrue(codec.parse("""{"name":"x","steps":[{"type":"wait","millis":999999999}]}""") is AppResult.Failure)
    }

    @Test
    fun rejectsMalformedJson() {
        assertTrue(codec.parse("{not json") is AppResult.Failure)
    }
}
