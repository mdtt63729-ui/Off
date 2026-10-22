package com.off.audioconverter.conversion

import android.content.ContentResolver
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.io.FileOutputStream

/** Options applied by the local FFmpeg pipeline. */
data class ConversionOptions(
    val bitrateKbps: Int = 192,
    val trimStartSeconds: Float = 0f,
    val trimEndSeconds: Float = 0f,
    val speed: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val volume: Float = 1f
)

sealed interface ConversionResult {
    data class Success(val outputFile: File) : ConversionResult
    data class Failure(val message: String) : ConversionResult
}

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
        val speed = options.speed.coerceIn(0.5f, 2f)
        val volume = options.volume.coerceIn(0f, 1.5f)
        val fadeIn = options.fadeInSeconds.coerceIn(0f, 10f)
        val fadeOut = options.fadeOutSeconds.coerceIn(0f, 10f)
        val input = File(cacheDir, "off_input_${System.currentTimeMillis()}.video")
        val output = File(cacheDir, "off_output_${System.currentTimeMillis()}.mp3")

        try {
            withContext(Dispatchers.Main) { onStatus("Preparing video…") }
            val source = contentResolver.openInputStream(inputUri)
                ?: return@withContext ConversionResult.Failure("Cannot open the selected video. Select it again and retry.")
            source.use { stream -> FileOutputStream(input).use { sink -> stream.copyTo(sink) } }
            if (!input.exists() || input.length() == 0L) {
                return@withContext ConversionResult.Failure("The selected video is empty or unreadable.")
            }

            withContext(Dispatchers.Main) { onStatus("Extracting and processing audio…") }
            val command = mutableListOf("-y")
            if (start > 0f) command += listOf("-ss", start.toString())
            command += listOf("-i", input.absolutePath)
            if (end > start) command += listOf("-t", (end - start).toString())
            command += listOf("-vn", "-codec:a", "libmp3lame", "-b:a", "${bitrate}k")

            val filters = mutableListOf<String>()
            if (kotlin.math.abs(speed - 1f) > 0.001f) filters += "atempo=${speed}"
            if (kotlin.math.abs(volume - 1f) > 0.001f) filters += "volume=${volume}"
            if (fadeIn > 0f) filters += "afade=t=in:st=0:d=${fadeIn}"
            if (fadeOut > 0f && end > start) {
                val outputDuration = ((end - start) / speed).coerceAtLeast(0.1f)
                val fadeStart = (outputDuration - fadeOut).coerceAtLeast(0f)
                filters += "afade=t=out:st=${fadeStart}:d=${fadeOut}"
            }
            if (filters.isNotEmpty()) command += listOf("-af", filters.joinToString(","))
            command += listOf("-f", "mp3", output.absolutePath)

            val session = suspendCancellableCoroutine { continuation ->
                val runningSession = FFmpegKit.executeWithArgumentsAsync(
                    command.toTypedArray(),
                    { completed -> if (continuation.isActive) continuation.resume(completed) },
                    null,
                    null
                )
                activeSessionId = runningSession.sessionId
                continuation.invokeOnCancellation { FFmpegKit.cancel(runningSession.sessionId) }
            }
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode) && output.exists() && output.length() > 0L) {
                ConversionResult.Success(output)
            } else {
                output.delete()
                val details = session.failStackTrace ?: session.allLogsAsString
                ConversionResult.Failure(
                    if (details.isNullOrBlank()) "Conversion failed. The video may not contain a supported audio stream."
                    else "Conversion failed. Try another video or a shorter trim range."
                )
            }
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
}
