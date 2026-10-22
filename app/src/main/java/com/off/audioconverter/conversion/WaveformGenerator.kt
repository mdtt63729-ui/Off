package com.off.audioconverter.conversion

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Draws a real waveform from the selected video's audio.
 *
 * FFmpeg renders the peaks (`showwavespic`) into a PNG, ideally with a
 * transparent background, which the editor shows instead of the placeholder
 * bars. Anything that fails simply returns null and the placeholder is used.
 */
object WaveformGenerator {

    suspend fun generate(context: Context, uri: Uri, cacheDir: File): File? = withContext(Dispatchers.IO) {
        val input = File(cacheDir, "wave_in_${System.currentTimeMillis()}.mp4")
        try {
            val source = context.contentResolver.openInputStream(uri) ?: return@withContext null
            source.use { stream -> FileOutputStream(input).use { sink -> stream.copyTo(sink) } }
            if (!input.exists() || input.length() == 0L) return@withContext null

            render(
                input,
                File(cacheDir, "wave_${System.currentTimeMillis()}.png"),
                "showwavespic=s=1000x220:colors=#FFB59E,format=rgba,colorkey=0x000000:0.06:0.0"
            )?.let { return@withContext it }

            // colorkey is not always available; a plain render still beats the placeholder.
            render(
                input,
                File(cacheDir, "wave_plain_${System.currentTimeMillis()}.png"),
                "showwavespic=s=1000x220:colors=#FFB59E"
            )
        } catch (_: Exception) {
            null
        } finally {
            input.delete()
        }
    }

    private fun render(input: File, output: File, filter: String): File? = runCatching {
        val session = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-i", input.absolutePath,
                "-filter_complex", filter,
                "-frames:v", "1",
                output.absolutePath
            )
        )
        if (ReturnCode.isSuccess(session.returnCode) && output.exists() && output.length() > 0L) output else null
    }.getOrNull()
}
