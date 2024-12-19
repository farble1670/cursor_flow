package org.jtb.cursorflow

import android.database.ContentObserver
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * A [ContentObserver] that throttles change notifications. See [throttle] for throttling
 * semantics.
 *
 * Use as such, for example:
 * ```
 * val observer = ThrottlingContentObserver(
 *    handler = Handler(Looper.getMainLooper()),
 *    coroutineScope = viewModelScope,
 *    throttleDuration = 100.milliseconds,
 * ) {
 *   // Handle change, may be throttled
 *   // ...
 * }
 *
 * ```
 */
class ThrottlingContentObserver(
    handler: Handler,
    coroutineScope: CoroutineScope,
    private val throttleDuration: Duration,
    private val throttleClock: FlowThrottleClock,
    private val onChangeThrottled: suspend () -> Unit
) : ContentObserver(handler) {

    private val observerEvents = MutableSharedFlow<Unit>(
        // Preserve non-blocking behavior for main thread
        extraBufferCapacity = 1
    )

    init {
        // Start collecting throttled events
        coroutineScope.launch {
            observerEvents
                .throttle(throttleDuration, throttleClock)
                .collect { onChangeThrottled() }
        }
    }

    override fun onChange(selfChange: Boolean) {
        // Emit event without blocking
        observerEvents.tryEmit(Unit)
    }
}