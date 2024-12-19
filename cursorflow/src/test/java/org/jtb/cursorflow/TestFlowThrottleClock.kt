package org.jtb.cursorflow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

internal class TestFlowThrottleClock(private val scheduler: TestCoroutineScheduler) : FlowThrottleClock {
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun nanoTime(): Long = scheduler.currentTime * 1_000_000
}
