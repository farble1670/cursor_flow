package org.jtb.cursorflow.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class CursorFlowExampleProvider : ContentProvider() {
  companion object {
    private const val AUTHORITY = "org.jtb.cursorflow.example"
    private const val TABLE_ITEMS = "items"

    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$TABLE_ITEMS")

    // UriMatcher constant - we only need one for the entire collection
    private const val ITEMS = 1

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
      addURI(AUTHORITY, TABLE_ITEMS, ITEMS)
    }

    // Column names
    const val COLUMN_ID = "_id"
    const val COLUMN_NAME = "name"
    const val COLUMN_VALUE = "value"

    // Random data generation
    private val adjectives = listOf(
        "Happy", "Sleepy", "Funny", "Clever", "Quick",
        "Lazy", "Busy", "Silly", "Wild", "Calm", "Angry",
        "Passive", "Distracted", "Complex",
    )
    private val nouns = listOf(
        "Penguin", "Kangaroo", "Elephant", "Giraffe", "Lion",
        "Tiger", "Panda", "Koala", "Monkey", "Bear", "Prawn",
        "Salamander", "Beluga", "Badger",
    )
  }

  private val scope = CoroutineScope(Dispatchers.Main + Job())
  private var updateJob: Job? = null

  override fun onCreate(): Boolean {
    // Run forever
    startPeriodicUpdates()
    return true
  }

  // Runs forever, do better
  private fun startPeriodicUpdates() {
    updateJob = scope.launch {
      while (isActive) {
        context?.contentResolver?.notifyChange(CONTENT_URI, null)
        delay(3.seconds)
      }
    }
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor {
    return if (uriMatcher.match(uri) == ITEMS) {
      // Create a cursor with our columns
      val cursor = MatrixCursor(arrayOf(COLUMN_ID, COLUMN_NAME, COLUMN_VALUE))

      // Add random items to cursor
      repeat(Random.nextInt(1, 10)) { index ->
        cursor.addRow(
            arrayOf(
                index,
                randomName,
                Random.nextInt(1, 100),
            )
        )
      }

      cursor.setNotificationUri(context!!.contentResolver, uri)
      cursor
    } else {
      throw IllegalArgumentException("Unknown URI: $uri")
    }
  }

  private val randomName: String
    get() = "${adjectives.random()} ${nouns.random()}"

  override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.$AUTHORITY.$TABLE_ITEMS"

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException("Read-only ContentProvider")
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
    throw UnsupportedOperationException("Read-only ContentProvider")
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?
  ): Int {
    throw UnsupportedOperationException("Read-only ContentProvider")
  }

  // This isn't called when you think it is
  override fun shutdown() {
    super.shutdown()
    scope.cancel() // Cancel all coroutines when the provider is shut down
  }
}