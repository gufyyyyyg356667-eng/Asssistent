package com.hhh.phoneagent.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val keys by viewModel.keys.collectAsState()
    var draft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gemini API keys", style = MaterialTheme.typography.titleLarge)
        Text("Paste one or more keys. They are stored encrypted on this device.")

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Keys (one per line)") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = draft.isNotBlank(),
                onClick = {
                    val added = viewModel.addKeys(draft)
                    message = if (added == 0) "No new valid key found" else "$added key(s) saved"
                    if (added > 0) draft = ""
                },
            ) { Text("Save") }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        message?.let { Text(it) }

        Spacer(Modifier.height(8.dp))
        Text("Saved keys: ${keys.size}", style = MaterialTheme.typography.titleMedium)

        keys.forEach { key ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(SettingsViewModel.mask(key))
                TextButton(onClick = { viewModel.removeKey(key) }) { Text("Remove") }
            }
        }
    }
}
