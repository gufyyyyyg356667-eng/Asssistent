package com.hhh.phoneagent.data.live

import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Thin WebSocket wrapper for the Live API. The key goes in the query string, as the Live
 * quick-start shows. The URL is never logged, and failures carry only the HTTP code and a message.
 */
class LiveSocket @Inject constructor(client: OkHttpClient) {

    interface Listener {
        fun onOpen()
        fun onText(text: String)
        fun onClosed(reason: String)
        fun onFailure(httpCode: Int?, message: String)
    }

    private val liveClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null

    fun connect(apiKey: String, listener: Listener) {
        val url = "$ENDPOINT?key=$apiKey"
        socket = liveClient.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                response.close()
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = listener.onText(text)

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(reason)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                response?.close()
                listener.onFailure(code, t.message ?: "Live connection failed")
            }
        })
    }

    fun send(message: JsonObject): Boolean = socket?.send(message.toString()) ?: false

    fun close() {
        socket?.close(NORMAL_CLOSE, "session ended")
        socket = null
    }

    private companion object {
        const val ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val NORMAL_CLOSE = 1000
    }
}
