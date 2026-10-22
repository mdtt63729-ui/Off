package com.off.audioconverter.player

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Short, val centerHz: Int, val levelMb: Int)

/**
 * Wraps Android's [Equalizer] audio effect and attaches it to the player's audio
 * session as soon as ExoPlayer reports one. Everything is guarded: an effect can
 * fail to attach on some devices and the UI should simply hide itself.
 */
object EqualizerController {

    private var effect: Equalizer? = null
    private var sessionId: Int = 0

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bands = MutableStateFlow<List<EqBand>>(emptyList())
    val bands: StateFlow<List<EqBand>> = _bands.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _range = MutableStateFlow(-1500 to 1500)
    val range: StateFlow<Pair<Int, Int>> = _range.asStateFlow()

    fun attach(newSessionId: Int) {
        if (newSessionId == 0 || newSessionId == sessionId) return
        release()
        sessionId = newSessionId
        runCatching {
            val eq = Equalizer(0, newSessionId)
            eq.enabled = false
            effect = eq
            _range.value = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
            _bands.value = readBands(eq)
            _presets.value = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
            _available.value = true
        }.onFailure {
            effect = null
            _available.value = false
            _bands.value = emptyList()
            _presets.value = emptyList()
        }
    }

    fun setEnabled(on: Boolean) {
        runCatching { effect?.enabled = on }
        _enabled.value = on
    }

    fun setBandLevel(index: Short, levelMb: Int) {
        runCatching { effect?.setBandLevel(index, levelMb.toShort()) }
        _bands.value = _bands.value.map { if (it.index == index) it.copy(levelMb = levelMb) else it }
    }

    fun usePreset(presetIndex: Short) {
        val eq = effect ?: return
        runCatching {
            eq.usePreset(presetIndex)
            _bands.value = readBands(eq)
            eq.enabled = true
            _enabled.value = true
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
        sessionId = 0
        _enabled.value = false
        _available.value = false
        _bands.value = emptyList()
        _presets.value = emptyList()
    }

    private fun readBands(eq: Equalizer): List<EqBand> =
        (0 until eq.numberOfBands.toInt()).map { i ->
            val band = i.toShort()
            EqBand(
                index = band,
                centerHz = eq.getCenterFreq(band) / 1000,
                levelMb = eq.getBandLevel(band).toInt()
            )
        }
}
