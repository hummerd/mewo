package com.mewo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.mewo.data.WordEntity

@Composable
internal fun WordControlsDialog(
    word: WordEntity,
    onUpdate: (String, Boolean, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var saving by remember(word.id) { mutableStateOf(false) }
    var error by remember(word.id) { mutableStateOf<String?>(null) }
    val update: (Boolean) -> Unit = { known ->
        saving = true
        error = null
        onUpdate(word.id, known) { result ->
            saving = false
            error = result
            if (result == null) onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(
                word.hebrew,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl),
                textAlign = TextAlign.End,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(word.english)
                Text(if (word.known) "Known · excluded from reminders" else "Shown ${word.shownCount} times")
                Text(
                    "Reset to start learning again. Mark as known to exclude this word from reminders.",
                )
                OutlinedButton(onClick = { update(false) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset counter")
                }
                if (!word.known) {
                    OutlinedButton(onClick = { update(true) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text("Mark as known")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
        },
    )
}
