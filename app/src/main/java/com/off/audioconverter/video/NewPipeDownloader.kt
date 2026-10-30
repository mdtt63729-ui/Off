package com.off.audioconverter.video

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * The HTTP client NewPipe Extractor asks for. It used to live in the music
 * package; the video resolver is the only thing that needs it now.
 */
class HttpDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 25000
        connection.instanceFollowRedirects = true
        connection.requestMethod = request.httpMethod()
        request.headers().forEach { (name, values) ->
            values.forEach { value -> connection.addRequestProperty(name, value) }
        }
        val body = request.dataToSend()
        if (body != null && body.isNotEmpty()) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val headers = connection.headerFields
            ?.filterKeys { it != null }
            ?.mapValues { entry -> entry.value ?: emptyList() }
            ?: emptyMap()
        return Response(code, connection.responseMessage ?: "", headers, text, connection.url.toString())
    }
}
