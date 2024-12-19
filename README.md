cursorflow
===========
Wrap a cursor and content changes in a Kotlin flow.

# Example usage

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

# Throttling

Include the `throttleDuration` argument to throttle emissions and updates. See documentation for the extension function `Flow.throttle` (see: `FlowThrottle.kt) for semantics.

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
      emit(2)                 // Throttled, not emitted
      delay(20.milliseconds)  // t=40
      emit(3)                 // Throttled
      delay(20.milliseconds)  // t=60
      emit(4)                 // Throttled
      delay(20.milliseconds)  // t=80
      emit(5)                 // Throttled
      delay(20.milliseconds)  // t=100, 5 is emitted
      delay(100.milliseconds) // t=200
      emit(6)                 // Immediately emitted  
      delay(20.milliseconds)  // t=220
      emit(7)                 // Throttled
      ...
    }
      .throttle(100.milliseconds)
```      
