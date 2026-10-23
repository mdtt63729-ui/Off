package com.off.audioconverter.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.off.audioconverter.music.Track

/**
 * Files a track into a playlist. It used to live in the player package, which is
 * gone; playlists are a library feature, so it belongs here.
 */
@Composable
fun AddToPlaylistDialog(track: Track, onDismiss: () -> Unit) {
    val playlists by LibraryStore.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlists.isEmpty()) {
                    Text("No playlists yet — create one below.", style = MaterialTheme.typography.bodySmall)
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LibraryStore.addToPlaylist(playlist.name, track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(playlist.name, modifier = Modifier.weight(1f))
                            Text("${playlist.tracks.size}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        LibraryStore.createPlaylist(name)
                        LibraryStore.addToPlaylist(name, track)
                    }
                    onDismiss()
                },
                enabled = newName.trim().isNotEmpty()
            ) { Text("Create & add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
