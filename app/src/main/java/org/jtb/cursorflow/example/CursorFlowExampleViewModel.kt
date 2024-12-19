package org.jtb.cursorflow.example

import android.app.Application
import android.database.Cursor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.jtb.cursorflow.ContentResult
import org.jtb.cursorflow.CursorFlow
import org.jtb.cursorflow.example.CursorFlowExampleProvider

class CursorFlowExampleViewModel(application: Application) : AndroidViewModel(application) {

  private val cursorFlow = CursorFlow.create(
      contentResolver = application.contentResolver,
      uri = CursorFlowExampleProvider.CONTENT_URI,
      cursorTransform = ::transformCursor,
      coroutineScope = viewModelScope
  )

  val items: StateFlow<ContentResult<Item>> = cursorFlow.state

  private fun transformCursor(cursor: Cursor): List<Item> = buildList {
    while (cursor.moveToNext()) {
      add(
          Item(
              id = cursor.getLong(cursor.getColumnIndexOrThrow(CursorFlowExampleProvider.COLUMN_ID)),
              name = cursor.getString(cursor.getColumnIndexOrThrow(CursorFlowExampleProvider.COLUMN_NAME)),
              value = cursor.getInt(cursor.getColumnIndexOrThrow(CursorFlowExampleProvider.COLUMN_VALUE))
          )
      )
    }
  }

  fun refresh() {
    cursorFlow.refresh()
  }
}