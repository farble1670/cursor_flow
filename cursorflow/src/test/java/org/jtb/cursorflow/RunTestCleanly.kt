package org.jtb.cursorflow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Use like [runTest], but cancels all children of the test coroutine context after the block.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestCleanly(
  block: suspend TestScope.(testDispatcher: TestDispatcher) -> Unit
) = runTest {

  val testDispatcher = UnconfinedTestDispatcher(testScheduler)

  try {
    block(this, testDispatcher)
  } finally {
    coroutineContext[Job]?.cancelChildren()
  }
}