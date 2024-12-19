package org.jtb.cursorflow.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jtb.cursorflow.ContentResult
import org.jtb.cursorflow.example.theme.CursorFlowTheme

class CursorFlowExampleActivity : ComponentActivity() {
  private val viewModel: CursorFlowExampleViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      CursorFlowTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          ItemList(
              modifier = Modifier.padding(innerPadding),
              viewModel = viewModel
          )
        }
      }
    }
  }
}

@Composable
fun ItemList(
  modifier: Modifier = Modifier,
  viewModel: CursorFlowExampleViewModel
) {
  val itemsResult by viewModel.items.collectAsState()

  when (val result = itemsResult) {
    is ContentResult.Success -> {
      if (result.data.isEmpty()) {
        // Show empty state
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
              text = "No items found",
              style = MaterialTheme.typography.bodyLarge
          )
        }
      } else {
        // Show list of items
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(result.data) { item ->
            ItemRow(item = item)
          }
        }
      }
    }
    is ContentResult.Error -> {
      // Show error state
      Column(
          modifier = modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
            text = "Error: ${result.exception.message}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
      }
    }
  }
}

@Composable
fun ItemRow(
  item: Item,
  modifier: Modifier = Modifier
) {
  Column(
      modifier = modifier
        .fillMaxWidth()
        .padding(8.dp)
  ) {
    Text(
        text = item.name,
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = "Value: ${item.value}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}