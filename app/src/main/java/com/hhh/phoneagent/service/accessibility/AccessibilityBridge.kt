package com.hhh.phoneagent.service.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only route from the rest of the app to the single Accessibility Service.
 * Tools and UI never hold a reference to the service itself.
 */
@Singleton
class AccessibilityBridge @Inject constructor() {

    private val controllerState = MutableStateFlow<ScreenController?>(null)
    private val connectedState = MutableStateFlow(false)

    val isConnected: StateFlow<Boolean> = connectedState.asStateFlow()

    internal fun attach(controller: ScreenController) {
        controllerState.value = controller
        connectedState.value = true
    }

    internal fun detach(controller: ScreenController) {
        if (controllerState.value === controller) {
            controllerState.value = null
            connectedState.value = false
        }
    }

    fun currentController(): ScreenController? = controllerState.value

    /** Suspends until the service connects, or returns null after [timeoutMillis]. */
    suspend fun awaitController(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): ScreenController? =
        withTimeoutOrNull(timeoutMillis) { controllerState.filterNotNull().first() }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
