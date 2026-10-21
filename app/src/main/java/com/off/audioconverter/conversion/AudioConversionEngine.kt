package com.off.audioconverter.conversion

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream

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
        bitrateKbps: Int,
        onStatus: (String) -> Unit
    ): ConversionResult = withContext(Dispatchers.IO) {
        val bitrate = bitrateKbps.coerceIn(128, 320)
        val input = File(cacheDir, "off_input_${System.currentTimeMillis()}.video")
        val output = File(cacheDir, "off_output_${System.currentTimeMillis()}.mp3")

        try {
            onStatus("Preparing video")
            val source = contentResolver.openInputStream(inputUri)
                ?: return@withContext ConversionResult.Failure("Cannot open the selected video.")
            source.use { stream ->
                FileOutputStream(input).use { sink -> stream.copyTo(sink) }
            }
            if (!input.exists() || input.length() == 0L) {
                return@withContext ConversionResult.Failure("The selected video is empty or unreadable.")
            }

            onStatus("Converting audio")
            val command = arrayOf(
                "-y", "-i", input.absolutePath,
                "-vn", "-codec:a", "libmp3lame",
                "-b:a", "${bitrate}k",
                "-f", "mp3", output.absolutePath
            )
            val session = FFmpegKit.executeWithArguments(command)
            activeSessionId = session.sessionId
            val returnCode = session.returnCode

            if (ReturnCode.isSuccess(returnCode) && output.exists() && output.length() > 0L) {
                ConversionResult.Success(output)
            } else {
                output.delete()
                val details = session.failStackTrace ?: session.allLogsAsString
                ConversionResult.Failure(
                    if (details.isNullOrBlank()) "Conversion failed. The video may not contain a supported audio stream."
                    else "Conversion failed. Try another video file."
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

    fun cancel() {
        val id = activeSessionId
        if (id != null) FFmpegKit.cancel(id)
    }
}
