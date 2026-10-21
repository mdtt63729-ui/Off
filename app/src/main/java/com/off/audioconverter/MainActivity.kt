package com.off.audioconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.off.audioconverter.conversion.AudioConversionEngine
import com.off.audioconverter.conversion.ConversionResult
import com.off.audioconverter.domain.ConversionStatus
import com.off.audioconverter.ui.theme.OffTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OffTheme { OffApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var bitrate by remember { mutableIntStateOf(192) }
    var status by remember { mutableStateOf(ConversionStatus.IDLE) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingOutput by remember { mutableStateOf<File?>(null) }
    var suggestedName by remember { mutableStateOf("off-audio.mp3") }

    val engine = remember { AudioConversionEngine(context.contentResolver, context.cacheDir) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedName = queryDisplayName(context.contentResolver, uri) ?: "Selected video"
            suggestedName = (selectedName ?: "audio").substringBeforeLast('.', selectedName ?: "audio") + ".mp3"
            status = ConversionStatus.IDLE
            statusMessage = null
        }
    }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { outputUri ->
        val sourceFile = pendingOutput
        if (outputUri == null || sourceFile == null) {
            sourceFile?.delete()
            pendingOutput = null
            if (status == ConversionStatus.PREPARING) status = ConversionStatus.CANCELLED
        } else {
            scope.launch {
                status = ConversionStatus.SAVING
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                            requireNotNull(output) { "Cannot open output destination." }
                            FileInputStream(sourceFile).use { input -> input.copyTo(output) }
                        }
                    }
                    status = ConversionStatus.COMPLETED
                    statusMessage = "MP3 saved successfully."
                    snackbar.showSnackbar("Audio saved")
                } catch (e: Exception) {
                    status = ConversionStatus.FAILED
                    statusMessage = e.message ?: "Could not save the MP3 file."
                } finally {
                    sourceFile.delete()
                    pendingOutput = null
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Off", fontWeight = FontWeight.Bold)
                        Text("Offline audio converter", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp))
                    }
                    Text("Turn video into audio", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choose a video stored on your device and create an MP3 locally. Your media is not uploaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { pickVideo.launch(arrayOf("video/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = status != ConversionStatus.CONVERTING && status != ConversionStatus.SAVING
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (selectedUri == null) "Choose video" else "Change video")
                    }
                }
            }

            AnimatedContent(
                targetState = selectedUri != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "selected-video"
            ) { hasVideo ->
                if (hasVideo) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.VideoFile, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Selected video", style = MaterialTheme.typography.titleMedium)
                                    Text(selectedName ?: "Video", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("MP3 bitrate", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(128, 192, 256, 320).forEach { kbps ->
                                    FilterChip(
                                        selected = bitrate == kbps,
                                        onClick = { bitrate = kbps },
                                        label = { Text("$kbps") }
                                    )
                                }
                            }
                            Text("Higher bitrate can increase file size; it cannot restore quality missing from the source.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = {
                                    val uri = selectedUri ?: return@Button
                                    status = ConversionStatus.PREPARING
                                    statusMessage = "Preparing conversion…"
                                    scope.launch {
                                        val result = engine.convert(uri, bitrate) { message ->
                                            statusMessage = message
                                        }
                                        when (result) {
                                            is ConversionResult.Success -> {
                                                pendingOutput = result.outputFile
                                                status = ConversionStatus.PREPARING
                                                createOutput.launch(suggestedName)
                                            }
                                            is ConversionResult.Failure -> {
                                                status = ConversionStatus.FAILED
                                                statusMessage = result.message
                                            }
                                        }
                                    }
                                },
                                enabled = status != ConversionStatus.PREPARING &&
                                    status != ConversionStatus.CONVERTING && status != ConversionStatus.SAVING,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Convert to MP3") }
                        }
                    }
                }
            }

            if (status in listOf(ConversionStatus.PREPARING, ConversionStatus.CONVERTING, ConversionStatus.SAVING)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Column(Modifier.weight(1f)) {
                                Text("Working on your audio", style = MaterialTheme.typography.titleMedium)
                                Text(statusMessage ?: "Please wait", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = {
                                engine.cancel()
                                pendingOutput?.delete()
                                pendingOutput = null
                                status = ConversionStatus.CANCELLED
                                statusMessage = "Conversion cancelled."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel") }
                    }
                }
            }

            if (status == ConversionStatus.COMPLETED || status == ConversionStatus.FAILED ||
                status == ConversionStatus.CANCELLED) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            if (status == ConversionStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = if (status == ConversionStatus.COMPLETED) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            when (status) {
                                ConversionStatus.COMPLETED -> "Conversion complete"
                                ConversionStatus.CANCELLED -> "Conversion cancelled"
                                else -> "Conversion failed"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(statusMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                status = ConversionStatus.IDLE
                                statusMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ready for another conversion") }
                    }
                }
            }

            Text(
                "Offline by design • No account • No media upload",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default bitrate: $bitrate kbps")
                    Text("Conversion runs locally on this device.")
                    Text("Version 1.0.0")
                }
            },
            confirmButton = {
                Button(onClick = { showSettings = false }) { Text("Done") }
            }
        )
    }
}

private fun queryDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri
): String? {
    return try {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (_: Exception) {
        null
    }
}
