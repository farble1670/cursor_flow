package org.jtb.cursorflow

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class ContentResult<out T> {
  data class Success<T>(val data: List<T>) : ContentResult<T>()
  data class Error(val exception: Throwable) : ContentResult<Nothing>()
}

/**
 * A [Flow] that observes changes to a [Uri] and emits the [Cursor] data, transformed into a list
 * of [C] content objects.
 *
 * @see [ContentResolver.query]
 */
class CursorFlow<C> private constructor(
  private val contentResolver: ContentResolver,
  /**
   * The [Uri] to query, and observe for changes.
   */
  private val uri: Uri,
  /**
   * The columns to return. If `null`, the behavior is content provider dependent.
   */
  private val projection: Array<String>? = null,
  /**
   * A filter declaring which rows to return, formatted as an SQL WHERE clause (excluding the
   * WHERE itself). Passing `null` will return all rows for the given URI.
   */
  private val selection: String? = null,
  /**
   * The arguments for the selection. If `null`, no arguments are passed.
   */
  private val selectionArgs: Array<String>? = null,
  /**
   * How to order the rows, formatted as an SQL ORDER BY clause (excluding the ORDER BY itself).
   * Passing `null` will use the default sort order, which may be unordered.
   */
  private val sortOrder: String? = null,

  /**
   * Transforms a [Cursor] into a list of type [C] content objects, consuming all rows
   * in the cursor.
   */
  private val cursorTransform: (Cursor) -> List<C>,
  /**
   * Compares two [C] content types to determine if they are equal. The default argument
   * relies on the [equals] implementation of the [C] content type.
   */
  private val contentComparator: (C, C) -> Boolean = { old, new -> old == new },

  private val coroutineScope: CoroutineScope,
  private val bgDispatcher: CoroutineDispatcher = DEFAULT_BG_DISPATCHER,

  /**
   * The duration to throttle emissions. The default is 1 second.
   *
   * @see [throttle]
   */
  private val throttleDuration: Duration = DEFAULT_THROTTLE_DURATION,
  private val throttleClock: FlowThrottleClock = object : FlowThrottleClock {
    override fun nanoTime(): Long = System.nanoTime()
  },
) {

  private val _state = MutableStateFlow<ContentResult<C>>(
      ContentResult.Success(emptyList())
  )
  val state: StateFlow<ContentResult<C>> = _state.asStateFlow()

  private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  init {
    coroutineScope.launch {
      merge(
          contentFlow(),
          refreshTrigger.map { queryContent() }
      )
        // Even though we're using StateFlow, we need custom equality checks to
        // incorporate the passed content comparator
        .distinctUntilChanged { old, new ->
          when {
            old is ContentResult.Success && new is ContentResult.Success ->
              old.data.contentEqualsBy(new.data, contentComparator)

            // Probable not useful, as errors typically don't implement
            // equality, but allow for it anyway.
            old is ContentResult.Error && new is ContentResult.Error ->
              old.exception == new.exception

            // Not possible
            else -> throw IllegalStateException("Unexpected state: $old, $new")
          }
        }
        .throttle(throttleDuration, throttleClock)
        .collect { result ->
          _state.emit(result)
        }
    }
    refresh()
  }

  /**
   * Refresh the content; immediately query and (possibly) emit the results, subject to
   * throttling.
   */
  fun refresh() {
    refreshTrigger.tryEmit(Unit)
  }

  private suspend fun queryContent() = withContext(bgDispatcher) {
    try {
      contentResolver.query(
          uri,
          projection,
          selection,
          selectionArgs,
          sortOrder
      )?.use(cursorTransform)?.let { ContentResult.Success(it) }
          ?: ContentResult.Success(emptyList())
    } catch (e: Exception) {
      ContentResult.Error(e)
    }
  }

  private fun contentFlow() = callbackFlow {
    val observer = ThrottlingContentObserver(
        handler = Handler(Looper.getMainLooper()),
        coroutineScope = coroutineScope,
        throttleDuration = throttleDuration,
        throttleClock = throttleClock,
    ) {
      coroutineScope.launch(bgDispatcher) {
        trySend(queryContent())
      }
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Registering content observer for URI: $uri")
    }

    contentResolver.registerContentObserver(uri, true, observer)

    // Initial query
    coroutineScope.launch(bgDispatcher) {
      trySend(queryContent())
    }

    awaitClose {
      contentResolver.unregisterContentObserver(observer)
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Unregistered content observer for URI: $uri")
      }
    }
  }
    .flowOn(bgDispatcher)

  companion object {
    private val TAG = CursorFlow::class.java.simpleName

    private val DEFAULT_THROTTLE_DURATION = 1.seconds
    private val DEFAULT_BG_DISPATCHER = Dispatchers.IO

    /**
     * @see [CursorFlow]
     */
    fun <T> create(
      contentResolver: ContentResolver,
      uri: Uri,
      projection: Array<String>? = null,
      selection: String? = null,
      selectionArgs: Array<String>? = null,
      sortOrder: String? = null,
      cursorTransform: (Cursor) -> List<T>,
      contentComparator: (T, T) -> Boolean = { old, new -> old == new },
      coroutineScope: CoroutineScope,
      bgDispatcher: CoroutineDispatcher = DEFAULT_BG_DISPATCHER,
      throttleDuration: Duration = DEFAULT_THROTTLE_DURATION,
      throttleClock: FlowThrottleClock = object : FlowThrottleClock {
        override fun nanoTime(): Long = System.nanoTime()
      },
    ): CursorFlow<T> = CursorFlow(
        contentResolver = contentResolver,
        uri = uri,
        projection = projection,
        selection = selection,
        selectionArgs = selectionArgs,
        sortOrder = sortOrder,
        cursorTransform = cursorTransform,
        contentComparator = contentComparator,
        coroutineScope = coroutineScope,
        bgDispatcher = bgDispatcher,
        throttleDuration = throttleDuration,
        throttleClock = throttleClock,
    )
  }
}

private fun <T> List<T>.contentEqualsBy(other: List<T>, compare: (T, T) -> Boolean): Boolean {
  return size == other.size && zip(other).all { (a, b) -> compare(a, b) }
}