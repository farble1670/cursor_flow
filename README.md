cursorflow
===========
Wrap a content resolver query, and content updates, in a Kotlin flow.

This library assumes you are exposing your database from a [content provider](https://developer.android.com/guide/topics/providers/content-providers). Content providers add lifecycle, IPC, and well-defined concurrency on top of a regular SQLite database.

Content providers seem to be infrequently used, in favor of modern database access architectures like [Room](https://developer.android.com/training/data-storage/room), hence, YMMV here. The main benefit, sharing data with other apps (processes), is a niche use case. 

## Example usage

Create:
```
  val myFlow: CursorFlow<String> = CursorFlow.create(
      contentResolver = context.contentResolver,

      uri = My.CONTENT_URI,
      projection = arrayOf(MyColumns.ID, MyColumns.NAME, MyColumns.CREATED),
      selection = "${MyColumns.CREATED} > ?",
      selectionArgs = arrayOf("0"),

      cursorTransform = { c -> c.getString(c.getColumnIndex(MyColumns.NAME)) },

      coroutineScope = viewModelScope,
  )
```

Observe:
```
  myFlow.collect { names ->
    names.forEach { name ->
      Log.d("CursorFlow", "Name: $name")
    }
  }
```

## Throttling

Include the `throttleDuration` argument to throttle emissions and updates. See documentation for the extension function `Flow.throttle` (see: `FlowThrottle.kt`) for semantics).

```
  val myFlow: CursorFlow<String> = CursorFlow.create(
    ...,
    throttleDuration = 100.milliseconds,
  )
```

`Flow.throttle` can be used in isolation on any `Flow`:

```
    flow {
      emit(1)                 // t=0, immediate, emit 1
      delay(20.milliseconds)  // t=20
      emit(2)                 // Throttled (not emitted)
      delay(20.milliseconds)  // t=40
      emit(3)                 // Throttled
      delay(20.milliseconds)  // t=60
      emit(4)                 // Throttled
      delay(20.milliseconds)  // t=80
      emit(5)                 // Throttled
      delay(20.milliseconds)  // t=100, 5 is emitted
      delay(100.milliseconds) // t=200
      emit(6)                 // 6 is immediately emitted  
      delay(20.milliseconds)  // t=220
      emit(7)                 // Throttled
      ...
    }
      .throttle(100.milliseconds)
```      

# Example

Find the example app in the `app/` module in this project.  