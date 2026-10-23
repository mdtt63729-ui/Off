package com.off.audioconverter.conversion

import android.content.ContentResolver
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/** Options applied by the local FFmpeg pipeline. */
data class ConversionOptions(
    val bitrateKbps: Int = 192,
    val trimStartSeconds: Float = 0f,
    val trimEndSeconds: Float = 0f,
    val speed: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val volume: Float = 1f,
    /** "Linear", "Smooth" or "Exponential" — mapped onto FFmpeg's afade curves. */
    val fadeCurve: String = "Smooth"
)

sealed interface ConversionResult {
    data class Success(val outputFile: File) : ConversionResult
    data class Failure(val message: String) : ConversionResult
}

/**
 * Local FFmpeg pipeline: copies the selected video into the cache, probes it, then
 * extracts/filters the audio into an MP3.
 *
 * The encoder is chosen at runtime because not every FFmpeg build ships the same
 * MP3 encoders — `libmp3lame` is preferred, `libshine` is the fallback.
 */
class AudioConversionEngine(
    private val contentResolver: ContentResolver,
    private val cacheDir: File
) {
    @Volatile private var activeSessionId: Long? = null

    suspend fun convert(
        inputUri: Uri,
        options: ConversionOptions,
        onStatus: (String) -> Unit
    ): ConversionResult = withContext(Dispatchers.IO) {
        val bitrate = options.bitrateKbps.coerceIn(128, 320)
        val start = options.trimStartSeconds.coerceAtLeast(0f)
        val end = options.trimEndSeconds
        val speed = options.speed.coerceIn(0.25f, 2f)
        val volume = options.volume.coerceIn(0f, 1.5f)
        val fadeIn = options.fadeInSeconds.coerceIn(0f, 10f)
        val fadeOut = options.fadeOutSeconds.coerceIn(0f, 10f)
        val input = File(cacheDir, "off_input_${System.currentTimeMillis()}.mp4")
        val output = File(cacheDir, "off_output_${System.currentTimeMillis()}.mp3")

        try {
            withContext(Dispatchers.Main) { onStatus("Reading the selected video…") }
            val source = contentResolver.openInputStream(inputUri)
                ?: return@withContext ConversionResult.Failure("Cannot open the selected video. Select it again and retry.")
            source.use { stream -> FileOutputStream(input).use { sink -> stream.copyTo(sink) } }
            if (!input.exists() || input.length() == 0L) {
                return@withContext ConversionResult.Failure("The selected video is empty or unreadable.")
            }

            // Best-effort probe so "no audio track" gets an honest message.
            withContext(Dispatchers.Main) { onStatus("Checking the audio track…") }
            val hasAudio = probeHasAudio(input)
            if (hasAudio == false) {
                return@withContext ConversionResult.Failure("This video has no audio track to extract. Pick a video that contains sound.")
            }

            withContext(Dispatchers.Main) { onStatus("Extracting and processing audio…") }
            val filters = mutableListOf<String>()
            // afade's curve names: tri (linear), qsin (smooth), exp (exponential).
            val curve = when (options.fadeCurve.lowercase()) {
                "linear" -> "tri"
                "exponential" -> "exp"
                else -> "qsin"
            }
            filters += atempoFilters(speed)
            if (kotlin.math.abs(volume - 1f) > 0.001f) filters += "volume=$volume"
            if (fadeIn > 0f) filters += "afade=t=in:st=0:d=$fadeIn:curve=$curve"
            if (fadeOut > 0f && end > start) {
                val outputDuration = ((end - start) / speed).coerceAtLeast(0.1f)
                val fadeStart = (outputDuration - fadeOut).coerceAtLeast(0f)
                filters += "afade=t=out:st=$fadeStart:d=$fadeOut:curve=$curve"
            }

            var lastDetail: String? = null
            for (encoder in ENCODERS) {
                output.delete()
                val command = mutableListOf("-y")
                if (start > 0f) command += listOf("-ss", start.toString())
                command += listOf("-i", input.absolutePath)
                if (end > start) command += listOf("-t", (end - start).toString())
                command += listOf("-vn", "-map", "0:a:0", "-ac", "2", "-ar", "44100")
                command += listOf("-codec:a", encoder, "-b:a", "${bitrate}k")
                if (filters.isNotEmpty()) command += listOf("-af", filters.joinToString(","))
                command += listOf("-f", "mp3", output.absolutePath)

                val session = runSession(command)
                if (session.first && output.exists() && output.length() > 0L) {
                    return@withContext ConversionResult.Success(output)
                }
                lastDetail = session.second
            }

            output.delete()
            ConversionResult.Failure(
                lastDetail
                    ?: "Conversion failed. The video may use an unsupported audio format — try another video."
            )
        } catch (e: Exception) {
            output.delete()
            ConversionResult.Failure(e.message ?: "Conversion failed unexpectedly.")
        } finally {
            input.delete()
            activeSessionId = null
        }
    }

    /** Compatibility overload retained for callers that only need bitrate conversion. */
    suspend fun convert(inputUri: Uri, bitrateKbps: Int, onStatus: (String) -> Unit): ConversionResult =
        convert(inputUri, ConversionOptions(bitrateKbps = bitrateKbps), onStatus)

    fun cancel() {
        activeSessionId?.let { FFmpegKit.cancel(it) }
    }

    /** Returns whether the run succeeded plus a short, human-readable reason on failure. */
    private suspend fun runSession(command: List<String>): Pair<Boolean, String?> {
        val session = suspendCancellableCoroutine { continuation ->
            val running = FFmpegKit.executeWithArgumentsAsync(
                command.toTypedArray(),
                { completed -> if (continuation.isActive) continuation.resume(completed) },
                null,
                null
            )
            activeSessionId = running.sessionId
            continuation.invokeOnCancellation { FFmpegKit.cancel(running.sessionId) }
        }
        activeSessionId = null
        val success = ReturnCode.isSuccess(session.returnCode)
        val detail = if (success) null else extractReason(session.allLogsAsString)
        return success to detail
    }

    /** null = unknown, true/false = probed result. */
    /**
     * A single `atempo` only covers 0.5x-2x, so anything outside that range is chained.
     * Without this the editor's 0.25x end silently did nothing.
     */
    private fun atempoFilters(speed: Float): List<String> {
        if (kotlin.math.abs(speed - 1f) <= 0.001f) return emptyList()
        val chain = mutableListOf<String>()
        var remaining = speed
        while (remaining < 0.5f) {
            chain += "atempo=0.5"
            remaining /= 0.5f
        }
        while (remaining > 2f) {
            chain += "atempo=2.0"
            remaining /= 2f
        }
        if (kotlin.math.abs(remaining - 1f) > 0.001f) chain += "atempo=$remaining"
        return chain
    }

    private fun probeHasAudio(file: File): Boolean? = try {
        val information = FFprobeKit.getMediaInformation(file.absolutePath).mediaInformation
        information?.streams?.any { it.type?.equals("audio", ignoreCase = true) == true }
    } catch (_: Exception) {
        null
    }

    private fun extractReason(logs: String?): String? {
        val lines = logs?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        val meaningful = lines.lastOrNull { line ->
            line.contains("Error", ignoreCase = true) ||
                line.contains("Invalid", ignoreCase = true) ||
                line.contains("Unknown", ignoreCase = true) ||
                line.contains("No such", ignoreCase = true) ||
                line.contains("failed", ignoreCase = true)
        } ?: lines.lastOrNull()
        return meaningful
            ?.removePrefix("ffmpeg version")
            ?.take(220)
            ?.let { "Conversion failed: $it" }
    }

    companion object {
        private val ENCODERS = listOf("libmp3lame", "libshine")
    }
}
