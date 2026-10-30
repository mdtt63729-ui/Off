package com.off.audioconverter

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.off.audioconverter.ui.CrashLog
import com.off.audioconverter.ui.OffHomeScreen
import com.off.audioconverter.ui.theme.OffTheme
import com.off.audioconverter.video.VideoDownloadService
import com.off.audioconverter.video.VideoDownloadTask
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** A link shared into Off from another app, if there is one. */
    private val sharedLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Keep the last failure on the device; Off sends nothing anywhere.
        CrashLog.install(applicationContext)
        sharedLink.value = linkFrom(intent)
        addOnNewIntentListener { sharedLink.value = linkFrom(it) }
        setContent {
            OffTheme {
                // If the previous launch was killed before it could draw anything,
                // show what killed it rather than repeating the crash silently.
                var report by remember { mutableStateOf(CrashLog.readUncaught(applicationContext)) }
                val shown = report
                if (shown != null) {
                    CrashReport(
                        text = shown,
                        onDismiss = {
                            CrashLog.clearUncaught(applicationContext)
                            report = null
                        }
                    )
                } else {
                    OffApp(initialLink = sharedLink.value)
                }
            }
        }
    }

    /** Pulls the first http(s) URL out of a shared message. */
    private fun linkFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        return Regex("https?://\\S+").find(text)?.value
    }
}

/** Shown when the last launch was killed, so the reason is readable instead of lost. */
@Composable
private fun CrashReport(text: String, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Off could not draw its screen",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            "This text is what I need to fix it. Please screenshot it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) {
            Text("Continue to Off")
        }
    }
}

/**
 * One screen: paste a link, pick a format, download.
 *
 * There is no navigation any more — no tabs, no player, no editor. The only other
 * surface is the settings sheet behind the gear, which holds the error log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffApp(initialLink: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(top = statusBarTop + 74.dp, bottom = navBarBottom + 28.dp)

    fun openSaved(task: VideoDownloadTask) {
        val saved = task.savedUri
        if (saved == null) {
            scope.launch { snackbar.showSnackbar("That file has not been saved yet.") }
            return
        }
        val isAudio = task.label.contains("MP3", true) || task.label.contains("M4A", true)
        val isImage = task.label.contains("Photo", true)
        val mime = when {
            isImage -> "image/*"
            isAudio -> "audio/*"
            else -> "video/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(saved), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            scope.launch { snackbar.showSnackbar("No app on this phone can open that file.") }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OffHomeScreen(
            contentPadding = contentPadding,
            initialLink = initialLink,
            onOpen = { task -> openSaved(task) },
            onDelete = { task -> VideoDownloadService.delete(context, task.id) },
            onNotice = { message -> scope.launch { snackbar.showSnackbar(message) } }
        )

        // Translucent top bar: the list scrolls beneath it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .padding(top = statusBarTop)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("Off", fontWeight = FontWeight.Bold)
                        Text(
                            "Video downloader",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }

        // Declared last so a message is drawn above everything else.
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarBottom + 16.dp)
        )
    }

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf(CrashLog.read(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Off", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                Text("Version 7.0.1", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "On-device video and photo downloading. Only download or keep content you are authorised to use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { logText = CrashLog.read(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Refresh the error log") }
                Text(
                    logText ?: "No errors recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (logText != null) {
                    TextButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, logText)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(share, "Send the error log"))
                            }
                        }
                    ) { Text("Share the error log") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
