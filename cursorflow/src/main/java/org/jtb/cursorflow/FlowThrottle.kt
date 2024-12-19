package org.jtb.cursorflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A clock that provides the current time in nanoseconds. Used for injecting a clock
 * into [throttle], for testing purposes.
 */
interface FlowThrottleClock {
  fun nanoTime(): Long
}

/**
 * Throttle a [Flow].
 *
 * If it's been more than [duration] since the last emission, the most recent value is
 * emitted immediately. If new values are emitted within [duration] of the last emission,
 * only the most recent value is emitted, after [duration] elapses.
 *
 * The initial value is always be emitted immediately.
 */
fun <R> Flow<R>.throttle(
  duration: Duration,
  clock: FlowThrottleClock = object : FlowThrottleClock {
    override fun nanoTime(): Long = System.nanoTime()
  }
): Flow<R> = channelFlow {
  var lastEmissionTime = -1L
  var pendingValue: R?
  var pendingJob: Job? = null

  collect { value ->
    val currentTime = clock.nanoTime()
    val elapsedNanos = if (lastEmissionTime == -1L) 0 else currentTime - lastEmissionTime

    when {
      lastEmissionTime == -1L -> {
        // First value, emit immediately
        send(value)
        lastEmissionTime = currentTime
      }
      elapsedNanos >= duration.inWholeNanoseconds -> {
        // Throttle window has passed, emit immediately
        pendingJob?.cancel()
        pendingJob = null
        send(value)
        lastEmissionTime = currentTime
      }
      else -> {
        // Throttle window has not passed, schedule emission
        pendingValue = value
        if (pendingJob == null) {
          pendingJob = launch {
            delay(duration - elapsedNanos.nanoseconds)
            pendingValue?.let { pending ->
              send(pending)
              lastEmissionTime = clock.nanoTime()
            }
            pendingJob = null
          }
        }
      }
    }
  }
}