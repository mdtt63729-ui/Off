package com.off.audioconverter.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Equalizer panel. Bands come from the device's own effect, so the number of
 * sliders and their frequency labels differ from phone to phone.
 */
@Composable
fun EqualizerPane(modifier: Modifier = Modifier) {
    val available by EqualizerController.available.collectAsState()
    val enabled by EqualizerController.enabled.collectAsState()
    val bands by EqualizerController.bands.collectAsState()
    val presets by EqualizerController.presets.collectAsState()
    val range by EqualizerController.range.collectAsState()

    if (!available) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text("Equalizer unavailable", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Start playing something first — the equalizer attaches to the audio session, and some devices don't offer one.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "On" else "Off",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Switch(checked = enabled, onCheckedChange = { EqualizerController.setEnabled(it) })
        }

        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.take(4).forEachIndexed { index, name ->
                    FilterChip(
                        selected = false,
                        onClick = { EqualizerController.usePreset(index.toShort()) },
                        label = { Text(name, maxLines = 1) }
                    )
                }
            }
        }

        bands.forEach { band ->
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatHz(band.centerHz),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        "%+.1f dB".format(band.levelMb / 100f),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Slider(
                    value = band.levelMb.toFloat(),
                    onValueChange = { EqualizerController.setBandLevel(band.index, it.toInt()) },
                    valueRange = range.first.toFloat()..range.second.toFloat(),
                    enabled = enabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatHz(hz: Int): String = if (hz >= 1000) "%.0fk".format(hz / 1000f) else "$hz"
