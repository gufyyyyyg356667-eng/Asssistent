package com.hhh.phoneagent.speech.live

import com.hhh.phoneagent.core.common.AppError
import com.hhh.phoneagent.core.common.AppResult
import com.hhh.phoneagent.core.common.failure
import com.hhh.phoneagent.data.live.LiveConfig
import com.hhh.phoneagent.data.live.LiveEvent
import com.hhh.phoneagent.data.live.LiveProtocol
import com.hhh.phoneagent.data.live.LiveSocket
import com.hhh.phoneagent.data.remote.gemini.ApiKeyLease
import com.hhh.phoneagent.data.remote.gemini.ApiKeyRotator
import com.hhh.phoneagent.domain.tool.ToolRegistry
import com.hhh.phoneagent.domain.usecase.AgentPrompts
import com.hhh.phoneagent.domain.usecase.ExecuteToolUseCase
import com.hhh.phoneagent.speech.MicArbiter
import com.hhh.phoneagent.speech.MicOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns one Gemini Live session: socket, microphone upstream, speaker downstream, and tool calls.
 * Tool calls run through [ExecuteToolUseCase], so the same risk gate and approval card apply.
 * The microphone is claimed as [MicOwner.LIVE], which blocks wake word and speech turns.
 */
@Singleton
class LiveController @Inject constructor(
    private val socket: LiveSocket,
    private val keys: ApiKeyRotator,
    private val arbiter: MicArbiter,
    private val microphone: LiveMicrophone,
    private val player: LivePlayer,
    private val tools: ExecuteToolUseCase,
    private val registry: ToolRegistry,
    private val json: Json,
) {
    private val stateFlow = MutableStateFlow<LiveState>(LiveState.Off)
    val state: StateFlow<LiveState> = stateFlow.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    @Volatile private var session: Job? = null
    @Volatile private var micJob: Job? = null

    suspend fun start(): AppResult<Unit> = lock.withLock {
        if (session != null) return@withLock AppResult.Success(Unit)
        if (!arbiter.claim(MicOwner.LIVE)) {
            return@withLock fail("Microphone is busy, please try again")
        }
        val lease = keys.acquire()
        if (lease == null) {
            arbiter.release(MicOwner.LIVE)
            return@withLock fail("No usable Gemini key. Add one in Gemini keys.")
        }
        val inbox = Channel<LiveEvent>(Channel.UNLIMITED)
        stateFlow.value = LiveState.Connecting
        socket.connect(lease.value, object : LiveSocket.Listener {
            override fun onOpen() {
                socket.send(LiveProtocol.setup(LiveConfig.MODEL, AgentPrompts.SYSTEM, registry.schemas()))
            }

            override fun onText(text: String) {
                val events = runCatching { LiveProtocol.parse(text, json) }.getOrElse {
                    listOf(LiveEvent.Error("Unreadable message from the Live server"))
                }
                events.forEach { inbox.trySend(it) }
            }

            override fun onClosed(reason: String) {
                inbox.trySend(LiveEvent.Error(reason.ifBlank { "Live session closed" }))
            }

            override fun onFailure(httpCode: Int?, message: String) {
                when (httpCode) {
                    401, 403 -> keys.reportRejected(lease)
                    429 -> keys.reportRateLimited(lease, null)
                }
                inbox.trySend(LiveEvent.Error(message))
            }
        })
        session = scope.launch { consume(inbox) }
        AppResult.Success(Unit)
    }

    /** Ends the session if one is running. Safe to call repeatedly. */
    fun stop() {
        scope.launch {
            lock.withLock {
                val job = session ?: return@withLock
                teardown(null)
                job.cancel()
            }
        }
    }

    private fun fail(message: String): AppResult.Failure {
        stateFlow.value = LiveState.Error(message)
        return failure(AppError.ErrorCode.ACTION_REJECTED, message)
    }

    private suspend fun consume(inbox: Channel<LiveEvent>) {
        for (event in inbox) {
            when (event) {
                LiveEvent.SetupComplete -> startAudio(inbox)
                is LiveEvent.Audio -> player.enqueue(event.pcm)
                LiveEvent.Interrupted -> player.interrupt()
                LiveEvent.TurnComplete -> Unit
                is LiveEvent.ToolCalls -> event.calls.forEach { call ->
                    scope.launch {
                        val result = tools.invoke(call)
                        socket.send(LiveProtocol.toolResponse(call, result))
                    }
                }
                is LiveEvent.Error -> {
                    teardown(event.message)
                    return
                }
            }
        }
        teardown("Live session ended")
    }

    private fun startAudio(inbox: Channel<LiveEvent>) {
        stateFlow.value = LiveState.Connected
        player.start(scope)
        val sender = scope.launch {
            try {
                microphone.stream { chunk -> socket.send(LiveProtocol.audioChunk(chunk)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                inbox.trySend(LiveEvent.Error(e.message ?: "Microphone error"))
            }
        }
        micJob = sender
    }

    /** Idempotent. Leaves [LiveState.Error] in place when [error] is set, so the UI can show it. */
    private fun teardown(error: String?) {
        micJob?.cancel()
        micJob = null
        player.stop()
        socket.close()
        arbiter.release(MicOwner.LIVE)
        session = null
        stateFlow.value = if (error == null) LiveState.Off else LiveState.Error(error)
    }
}
