package org.jtb.cursorflow

import android.app.Application
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FlowThrottleTest {

  @Test
  fun `first value emits immediately`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val result = flowOf(1)
      .throttle(100.milliseconds, clock)
      .toList()

    assertEquals(listOf(1), result)
  }

  @Test
  fun `values within throttle window are delayed and consolidated`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val throttleDuration = 100.milliseconds

    val results = flow {
      emit(1)                    // Immediate, emit 1
      advanceTimeBy(50.milliseconds)  // t=50
      emit(2)                    // Throttled
      advanceTimeBy(20.milliseconds)  // t=70
      emit(3)                    // Throttled
      advanceTimeBy(100.milliseconds) // t=170
      // Last value emitted, 3
    }
      .throttle(throttleDuration, clock)
      .toList()

    assertEquals(listOf(1, 3), results)
  }

  @Test
  fun `values after throttle window are emitted immediately`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val throttleDuration = 100.milliseconds

    val results = flow {
      emit(1)                       // Immediate
      advanceTimeBy(150.milliseconds)  // Past throttle window
      emit(2)                       // Should emit immediately
      advanceTimeBy(150.milliseconds)  // Past throttle window
      emit(3)                       // Should emit immediately
    }
      .throttle(throttleDuration, clock)
      .toList()

    assertEquals(listOf(1, 2, 3), results)
  }

  @Test
  fun `multiple values within window only emit last value`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val throttleDuration = 100.milliseconds

    val results = flow {
      emit(1)                      // t=0, immediate, emit 1
      advanceTimeBy(20.milliseconds) // t=20
      emit(2)                      // Throttled
      advanceTimeBy(20.milliseconds) // t=40
      emit(3)                      // Throttled
      advanceTimeBy(20.milliseconds) // t=60
      emit(4)                      // Throttled
      advanceTimeBy(20.milliseconds) // t=80
      emit(5)                      // Throttled
      advanceTimeBy(100.milliseconds) // t=180, allow pending value to emit
    }
      .throttle(throttleDuration, clock)
      .toList()

    assertEquals(listOf(1, 5), results)
  }

  @Test
  fun `empty flow emits nothing`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val results = flow<Int> { }
      .throttle(100.milliseconds, clock)
      .toList()

    assertEquals(emptyList<Int>(), results)
    advanceTimeBy(200.milliseconds)
    assertEquals(emptyList<Int>(), results)
  }

  @Test
  fun `values emitted with exact throttle duration gap`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val throttleDuration = 100.milliseconds

    val results = flow {
      emit(1)                        // Immediate
      advanceTimeBy(100.milliseconds)     // Exactly throttle duration
      emit(2)                       // Should emit immediately
      advanceTimeBy(100.milliseconds)     // Exactly throttle duration
      emit(3)                       // Should emit immediately
    }
      .throttle(throttleDuration, clock)
      .toList()

    assertEquals(listOf(1, 2, 3), results)
  }

  @Test
  fun `rapid emissions only emit first and last`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val throttleDuration = 100.milliseconds

    val results = flow {
      emit(1)                      // Immediate
      advanceTimeBy(1.milliseconds)  // t=1
      emit(2)                      // Throttled
      emit(3)                      // Throttled
      emit(4)                      // Throttled
      emit(5)                      // Throttled
      advanceTimeBy(100.milliseconds) // Allow pending value to emit
    }
      .throttle(throttleDuration, clock)
      .toList()

    assertEquals(listOf(1, 5), results)
  }

  @Test
  fun `zero duration throttle emits all values`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)

    val results = flowOf(1, 2, 3, 4, 5)
      .throttle(0.milliseconds, clock)
      .toList()

    assertEquals(listOf(1, 2, 3, 4, 5), results)
  }

  @Test
  fun `pending value is cancelled when new value arrives`() = runTest {
    val clock = TestFlowThrottleClock(testScheduler)
    val throttleDuration = 100.milliseconds

    val results = flow {
      emit(1)                       // Immediate
      advanceTimeBy(50.milliseconds)  // t=50
      emit(2)                       // Throttled
      advanceTimeBy(20.milliseconds)  // t=70
      emit(3)                       // Replaces 2
      advanceTimeBy(20.milliseconds)  // t=90
      emit(4)                       // Replaces 3
      advanceTimeBy(100.milliseconds) // Allow pending value to emit
    }
      .throttle(throttleDuration, clock)
      .toList()

    assertEquals(listOf(1, 4), results)
  }
}