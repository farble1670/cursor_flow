package org.jtb.cursorflow

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds

/**
 * An [Exception] that implements equality... to test duplicate error emissions.
 */
private class EqualableException(message: String) : RuntimeException(message) {
  override fun equals(other: Any?): Boolean {
    return other is EqualableException && other.message == message
  }

  override fun hashCode(): Int {
    return message.hashCode()
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CursorFlowTest {
  private lateinit var contentResolver: ContentResolver
  private lateinit var testUri: Uri
  private val testDispatcher = StandardTestDispatcher()

  // All tests must call cancel on this, otherwise the test framework will fail because of
  // active coroutines
  private lateinit var testScope: TestScope

  @Before
  fun setup() {
    contentResolver = org.mockito.kotlin.mock()
    testUri = Uri.parse("content://test/path")
    testScope = TestScope(testDispatcher)
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun newCursor(columns: Array<String>, vararg rows: Array<Any>): Cursor {
    return MatrixCursor(columns).apply {
      rows.forEach { addRow(it) }
    }
  }

  private fun mockQueryResult(columns: Array<String>, vararg rows: Array<Any>) {
    fun newCursor(columns: Array<String>, vararg rows: Array<Any>): Cursor {
      return MatrixCursor(columns).apply {
        rows.forEach { addRow(it) }
      }
    }

    org.mockito.kotlin.whenever(
        contentResolver.query(
            org.mockito.kotlin.eq(testUri),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull()
        )
    ).thenAnswer { newCursor(columns, *rows) }
  }

  @Test
  fun `flow emits empty initial value`() = runTestCleanly {
    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = this,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(emptyList())
        ),
        actual = collectedStates
    )
  }


  @Test
  fun `flow emits empty initial data from cursor`() = runTestCleanly {
    mockQueryResult(columns = arrayOf("column1"))
    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = this,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(emptyList())
        ),
        actual = collectedStates
    )
  }

  @Test
  fun `flow emits initial data from cursor`() = runTestCleanly {
    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
    )

    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    // Then
    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3"))
        ),
        actual = collectedStates,
    )
  }

  @Test
  fun `flow emits updated data from cursor`() = runTestCleanly {
    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
    )

    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
        throttleClock = TestFlowThrottleClock(testScope.testScheduler),
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
        arrayOf("value4"),
    )

    // Capture the ContentObserver that was registered
    org.mockito.kotlin.verify(contentResolver).registerContentObserver(
        org.mockito.kotlin.eq(testUri),
        org.mockito.kotlin.eq(true),
        org.mockito.kotlin.any()
    )

    // Get the captured observer
    val observerCaptor = org.mockito.ArgumentCaptor.forClass(ContentObserver::class.java)
    org.mockito.kotlin.verify(contentResolver).registerContentObserver(
        org.mockito.kotlin.eq(testUri),
        org.mockito.kotlin.eq(true),
        observerCaptor.capture()
    )

    // Manually trigger the observer
    observerCaptor.value.onChange(true, testUri)

    advanceUntilIdle()

    // Then
    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3")),
            ContentResult.Success(
                listOf(
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                )
            )
        ),
        actual = collectedStates,
    )
  }

  @Test
  fun `flow emits updated data only after throttle`() = runTestCleanly {
    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
    )

    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 100.milliseconds,
        throttleClock = TestFlowThrottleClock(testScope.testScheduler),
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    // Capture the ContentObserver so we can trigger it
    org.mockito.kotlin.verify(contentResolver).registerContentObserver(
        org.mockito.kotlin.eq(testUri),
        org.mockito.kotlin.eq(true),
        org.mockito.kotlin.any()
    )
    val observerCaptor = org.mockito.ArgumentCaptor.forClass(ContentObserver::class.java)
    org.mockito.kotlin.verify(contentResolver).registerContentObserver(
        org.mockito.kotlin.eq(testUri),
        org.mockito.kotlin.eq(true),
        observerCaptor.capture()
    )

    // Verify initial value
    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3")),
        ),
        actual = collectedStates,
    )

    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
        arrayOf("value4"),
    )

    // Trigger the observer after updated cursor is set
    observerCaptor.value.onChange(true, testUri)

    advanceTimeBy(10.milliseconds)

    // We haven't advanced time enough to trigger the throttle
    // Should still be initial value
    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3")),
        ),
        actual = collectedStates,
    )

    advanceTimeBy(100.milliseconds)

    // Advanced past throttle, should have updated value
    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3")),
            ContentResult.Success(
                listOf(
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                )
            )
        ),
        actual = collectedStates,
    )
  }

  @Test
  fun `flow emits error when query throws exception`() = runTestCleanly {
    // Given
    org.mockito.kotlin.whenever(
        contentResolver.query(
            org.mockito.kotlin.eq(testUri),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull()
        )
    ).thenThrow(EqualableException("Test exception"))

    val collectedStates = mutableListOf<ContentResult<Any>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Error(EqualableException("Test exception"))
        ),
        actual = collectedStates
    )
  }

  @Test
  fun `flow emits results when refresh is invoked`() = runTestCleanly {
    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
    )

    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    mockQueryResult(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
        arrayOf("value4"),
    )

    flow.refresh()

    advanceUntilIdle()

    // Then
    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3")),
            ContentResult.Success(
                listOf(
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                )
            )
        ),
        actual = collectedStates,
    )
  }

  @Test
  fun `flow does not emit duplicate successes`() = runTestCleanly {
    fun createCursor() = newCursor(
        columns = arrayOf("column1"),
        arrayOf("value1"),
        arrayOf("value2"),
        arrayOf("value3"),
    )


    org.mockito.kotlin.whenever(
        contentResolver.query(
            org.mockito.kotlin.eq(testUri),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull()
        )
    ).thenAnswer { createCursor() }  // Create a new cursor each time

    val collectedStates = mutableListOf<ContentResult<String>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    // When
    flow.refresh()

    advanceUntilIdle()

    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Success(listOf("value1", "value2", "value3"))
        ),
        actual = collectedStates,
    )
  }

  @Test
  fun `flow does not emit duplicate errors`() = runTestCleanly {
    org.mockito.kotlin.whenever(
        contentResolver.query(
            org.mockito.kotlin.eq(testUri),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull(),
            org.mockito.kotlin.isNull()
        )
    ).thenThrow(EqualableException("Test exception"))

    val collectedStates = mutableListOf<ContentResult<Any>>()

    val flow = CursorFlow.Companion.create(
        contentResolver = contentResolver,
        uri = testUri,
        cursorTransform = { c ->
          buildList {
            while (c.moveToNext()) {
              add(c.getString(0))
            }
          }
        },
        coroutineScope = testScope,
        bgDispatcher = testDispatcher,
        throttleDuration = 1.milliseconds,
    )

    testScope.launch {
      flow.state.collect { state ->
        collectedStates.add(state)
      }
    }

    advanceUntilIdle()

    flow.refresh()

    advanceUntilIdle()

    assertContentResultsEquals(
        expected = listOf(
            ContentResult.Success(listOf()),
            ContentResult.Error(EqualableException("Test exception"))
        ),
        actual = collectedStates,
    )
  }
}

private fun <T> assertContentResultsEquals(
  expected: List<ContentResult<T>>,
  actual: List<ContentResult<T>>
) {
  assertEquals("Lists have different sizes:", expected, actual)
  expected.zip(actual).forEachIndexed { index, (exp, act) ->
    assertEquals("Items at position $index differ:", exp, act)
  }
}