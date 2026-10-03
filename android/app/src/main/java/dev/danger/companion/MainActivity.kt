package dev.danger.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import dev.danger.companion.face.BloubRenderer
import dev.danger.companion.push.CompanionStreamService
import dev.danger.companion.widget.FaceWidget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EVENT_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

private val BG_OPTIONS = listOf(
    SettingsStore.TRANSPARENT,
    "#11151C",
    "#000000",
    "#1E293B",
    "#FFFFFF",
    "#4F9CF9",
    "#9B7BFF",
)

private fun parseComposeColor(value: String?): Color? {
    if (value == null || value.equals(SettingsStore.TRANSPARENT, ignoreCase = true)) return null
    return try {
        Color(android.graphics.Color.parseColor(value))
    } catch (_: Exception) {
        Color(0xFF11151C)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompanionScreen()
            }
        }
    }
}

@Composable
private fun CompanionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by CompanionStore.flow(context).collectAsState(initial = CompanionState.defaultSleepy())
    val settings by SettingsStore.flow(context).collectAsState(initial = null)
    val history by HistoryStore.flow(context).collectAsState(initial = emptyList())

    var relayInput by remember { mutableStateOf<String?>(null) }
    var topicInput by remember { mutableStateOf<String?>(null) }
    var tokenInput by remember { mutableStateOf<String?>(null) }
    var bgInput by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings) {
        val current = settings ?: return@LaunchedEffect
        if (relayInput == null) relayInput = current.relayUrl
        if (topicInput == null) topicInput = current.topic
        if (tokenInput == null) tokenInput = current.token
        if (bgInput == null) bgInput = current.bgColor
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { SettingsStore.setEnabled(context, true) }
            CompanionStreamService.start(context)
        }
    }

    fun startListening() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            scope.launch { SettingsStore.setEnabled(context, true) }
            CompanionStreamService.start(context)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun stopListening() {
        scope.launch { SettingsStore.setEnabled(context, false) }
        CompanionStreamService.stop(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Android Companion", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Fires an explicit dev.danger.companion.PUSH to the home-screen face widget, " +
                "or listens to an ntfy relay directly.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val previewBg = parseComposeColor(bgInput)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .then(if (previewBg != null) Modifier.background(previewBg) else Modifier)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = BloubRenderer.render(state, 200, 200).asImageBitmap(),
                    contentDescription = "Current face preview",
                    modifier = Modifier.size(200.dp),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Emotion: ${state.emotion.name}")
                Text(text = "Instance: ${state.instanceLabel} (${state.instanceId})")
                Text(text = "Color: #%06X".format(0xFFFFFF and state.colorArgb))
                Text(text = "Text: ${state.text ?: "-"}")
                Text(text = "Updated: ${state.updatedAt}")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { scope.launch { HistoryStore.clear(context) } }) {
                        Text(text = "Clear")
                    }
                }
                if (history.isEmpty()) {
                    Text(text = "No messages yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .height(320.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(history) { event -> MessageRow(event) }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "ntfy relay", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Listening: ${if (settings?.enabled == true) "enabled" else "disabled"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = relayInput.orEmpty(),
                    onValueChange = { relayInput = it },
                    label = { Text("Relay URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = topicInput.orEmpty(),
                    onValueChange = { topicInput = it },
                    label = { Text("Topic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tokenInput.orEmpty(),
                    onValueChange = { tokenInput = it },
                    label = { Text("Token (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            SettingsStore.write(
                                context,
                                Settings(
                                    relayUrl = relayInput.orEmpty(),
                                    topic = topicInput.orEmpty(),
                                    token = tokenInput.orEmpty(),
                                    enabled = settings?.enabled == true,
                                    bgColor = settings?.bgColor ?: SettingsStore.DEFAULT_BG_COLOR,
                                ),
                            )
                            if (settings?.enabled == true) {
                                CompanionStreamService.restart(context)
                            }
                        }
                    },
                ) {
                    Text(text = "Save")
                }
                if (settings?.enabled == true) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { stopListening() },
                    ) {
                        Text(text = "Stop listening")
                    }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startListening() },
                    ) {
                        Text(text = "Start listening")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Widget background", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Choose the widget background (transparent included).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BG_OPTIONS.forEach { option ->
                        val swatch = parseComposeColor(option)
                        val selected = (bgInput ?: SettingsStore.DEFAULT_BG_COLOR) == option
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(if (swatch != null) Modifier.background(swatch) else Modifier)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    bgInput = option
                                    scope.launch {
                                        SettingsStore.setBgColor(context, option)
                                        FaceWidget().updateAll(context)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (swatch == null) {
                                Text(text = "T", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(event: CompanionEvent) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.emotion.name,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = event.text ?: "(no text)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
            )
            Text(
                text = EVENT_TIME_FORMAT.format(Date(event.ts)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            text = "${event.instanceLabel} (${event.instanceId})",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
