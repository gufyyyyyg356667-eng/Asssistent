package com.hhh.phoneagent.domain.usecase

import com.hhh.phoneagent.domain.model.ChatMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Shared in-memory conversation for voice and (later) text turns. Trimmed so it always starts with a user message. */
@Singleton
class AgentSession @Inject constructor() {

    private val mutex = Mutex()
    private val messages = ArrayList<ChatMessage>()

    suspend fun append(message: ChatMessage) = mutex.withLock {
        messages.add(message)
        trim()
    }

    suspend fun snapshot(): List<ChatMessage> = mutex.withLock { messages.toList() }

    private fun trim() {
        if (messages.size <= MAX_MESSAGES) return
        var cut = messages.size - MAX_MESSAGES
        while (cut < messages.size && messages[cut] !is ChatMessage.User) cut++
        repeat(cut) { messages.removeAt(0) }
    }

    private companion object {
        const val MAX_MESSAGES = 40
    }
}
