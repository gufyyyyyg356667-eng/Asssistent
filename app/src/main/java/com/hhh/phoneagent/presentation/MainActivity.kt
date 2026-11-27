package com.hhh.phoneagent.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.hhh.phoneagent.domain.model.VoiceState
import com.hhh.phoneagent.presentation.voice.VoiceViewModel
import com.hhh.phoneagent.service.accessibility.AccessibilityBridge
import com.hhh.phoneagent.service.overlay.OverlayController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bridge: AccessibilityBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    bridge = bridge,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    bridge: AccessibilityBridge,
    onOpenAccessibility: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val connected by bridge.isConnected.collectAsState()
    val wakeEnabled by viewModel.wakeWordEnabled.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    var micGranted by remember { mutableStateOf(hasMicPermission(context)) }
    var pendingWakeEnable by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        micGranted = hasMicPermission(context)
        if (micGranted && pendingWakeEnable) viewModel.setWakeWordEnabled(true)
        pendingWakeEnable = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Accessibility: ${if (connected) "ON" else "OFF"}")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenAccessibility) { Text("Open Accessibility Settings") }
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wake word")
            Spacer(Modifier.height(0.dp))
            Switch(
                checked = wakeEnabled,
                onCheckedChange = { enable ->
                    if (enable && !micGranted) {
                        pendingWakeEnable = true
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS),
                        )
                    } else {
                        viewModel.setWakeWordEnabled(enable)
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        var bubbleOn by remember { mutableStateOf(false) }
        val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            bubbleOn = OverlayController.start(context)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Floating bubble")
            Spacer(Modifier.height(0.dp))
            Switch(
                checked = bubbleOn,
                onCheckedChange = { enable ->
                    if (enable) {
                        if (OverlayController.canDraw(context)) {
                            bubbleOn = OverlayController.start(context)
                        } else {
                            overlayLauncher.launch(OverlayController.permissionIntent(context))
                        }
                    } else {
                        OverlayController.stop(context)
                        bubbleOn = false
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        Button(onClick = viewModel::talkNow, enabled = micGranted) { Text("Talk") }
        Spacer(Modifier.height(16.dp))
        Text("Status: ${describe(voiceState)}")
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun describe(state: VoiceState): String = when (state) {
    VoiceState.Idle -> "Idle"
    VoiceState.WakeListening -> "Listening for wake word"
    is VoiceState.Listening -> "Listening ${state.partialText}".trim()
    VoiceState.Thinking -> "Thinking"
    VoiceState.Speaking -> "Speaking"
    is VoiceState.Error -> "Error: ${state.message}"
}
