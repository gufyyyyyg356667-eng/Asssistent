package com.hhh.phoneagent.data.live

import com.hhh.phoneagent.domain.model.ToolCall
import com.hhh.phoneagent.domain.tool.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LiveProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun setupUsesModelResourceNameAndAudioOnly() {
        val setup = LiveProtocol.setup("gemini-3.8-live", "hello", emptyList())["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", setup["model"]!!.jsonPrimitive.content)
        assertEquals("AUDIO", setup["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        assertFalse(setup.containsKey("tools"))
    }

    @Test
    fun audioChunkIsBase64Pcm16kMono() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val audio = LiveProtocol.audioChunk(pcm)["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", audio["mimeType"]!!.jsonPrimitive.content)
        assertArrayEquals(pcm, Base64.getDecoder().decode(audio["data"]!!.jsonPrimitive.content))
    }

    @Test
    fun parsesSetupCompleteAndTurnComplete() {
        val events = LiveProtocol.parse("""{"setupComplete":{}}""", json)
        assertEquals(LiveEvent.SetupComplete, events.single())

        val turn = LiveProtocol.parse("""{"serverContent":{"turnComplete":true}}""", json)
        assertEquals(LiveEvent.TurnComplete, turn.single())
    }

    @Test
    fun parsesModelAudioAndInterrupt() {
        val pcm = byteArrayOf(9, 8, 7)
        val encoded = Base64.getEncoder().encodeToString(pcm)
        val text = """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"$encoded"}}]},"interrupted":true}}"""

        val events = LiveProtocol.parse(text, json)
        assertArrayEquals(pcm, (events[0] as LiveEvent.Audio).pcm)
        assertEquals(LiveEvent.Interrupted, events[1])
    }

    @Test
    fun parsesToolCalls() {
        val text = """{"toolCall":{"functionCalls":[{"id":"c1","name":"read_screen","args":{"depth":2}}]}}"""
        val call = (LiveProtocol.parse(text, json).single() as LiveEvent.ToolCalls).calls.single()
        assertEquals("c1", call.id)
        assertEquals("read_screen", call.name)
        assertEquals("2", call.args["depth"]!!.jsonPrimitive.content)
    }

    @Test
    fun parsesServerError() {
        val event = LiveProtocol.parse("""{"error":{"message":"bad key"}}""", json).single()
        assertEquals(LiveEvent.Error("bad key"), event)
    }

    @Test
    fun toolResponseEchoesCallIdAndStatus() {
        val call = ToolCall(id = "c9", name = "list_workflows", args = JsonObject(emptyMap()))
        val message = LiveProtocol.toolResponse(call, ToolResult.ok("none"))
        val response = message["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
        assertEquals("c9", response["id"]!!.jsonPrimitive.content)
        assertEquals("OK", response["response"]!!.jsonObject["status"]!!.jsonPrimitive.content)
        assertTrue(response.containsKey("response"))
    }
}
