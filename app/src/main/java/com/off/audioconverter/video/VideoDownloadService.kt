package com.off.audioconverter.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.off.audioconverter.MainActivity
import com.off.audioconverter.ui.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/** Observable registry of video downloads. */
object VideoDownloadStore {
    private val _tasks = MutableStateFlow<List<VideoDownloadTask>>(emptyList())
    val tasks: StateFlow<List<VideoDownloadTask>> = _tasks.asStateFlow()

    fun add(task: VideoDownloadTask) {
        _tasks.update { current -> if (current.any { it.id == task.id }) current else listOf(task) + current }
    }

    fun update(id: String, transform: (VideoDownloadTask) -> VideoDownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun find(id: String): VideoDownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun isActive(status: VideoDownloadStatus): Boolean =
        status == VideoDownloadStatus.QUEUED || status == VideoDownloadStatus.RUNNING ||
            status == VideoDownloadStatus.PAUSED

    fun clearFinished() {
        _tasks.update { list -> list.filter { isActive(it.status) } }
    }
}

/** Thrown to unwind a download when the user asked for a pause. */
private class DownloadPaused : Exception("Paused")

/**
 * Downloads a video, or just its audio.
 *
 * Runs as a `dataSync` foreground service with a determinate progress
 * notification. Three shapes of job:
 *  - **muxed video** — written straight out;
 *  - **video-only** — paired with the best audio and joined with FFmpeg;
 *  - **yt-dlp** — yt-dlp fetches the file itself, on a worker thread.
 *
 * Every entry point is defensive: a failure marks the download failed and shows
 * why, rather than letting an exception escape and take the process down.
 */
class VideoDownloadService : Service() {

    private data class Pending(val task: VideoDownloadTask, val selection: DownloadSelection, val resume: Boolean)

    companion object {
        private const val CHANNEL_ID = "off_video_downloads"
        private const val NOTIFICATION_ID = 4720
        const val ACTION_CANCEL = "com.off.audioconverter.video.CANCEL"
        private const val ACTION_RESUME = "com.off.audioconverter.video.RESUME"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THUMB = "thumb"
        private const val EXTRA_VIDEO_URL = "videoUrl"
        private const val EXTRA_AUDIO_URL = "audioUrl"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_FORMAT = "format"
        private const val EXTRA_MP3_BITRATE = "mp3Bitrate"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_PAGE_URL = "pageUrl"
        private const val EXTRA_YTDLP_SELECTOR = "ytDlpSelector"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_IS_IMAGE = "isImage"
        private const val EXTRA_DURATION = "duration"

        /** yt-dlp's intermediate parts, e.g. name.f137.mp4, which are not the result. */
        private val INTERMEDIATE_PART = Regex("\\.f\\d+\\.")

        @Volatile private var instance: VideoDownloadService? = null

        fun enqueue(context: Context, info: VideoInfo, selection: DownloadSelection, taskId: String) {
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                putExtra(EXTRA_ID, taskId)
                putExtra(EXTRA_TITLE, info.title)
                putExtra(EXTRA_THUMB, info.thumbnailUrl)
                putExtra(EXTRA_VIDEO_URL, selection.videoUrl)
                putExtra(EXTRA_AUDIO_URL, selection.audioUrl)
                putExtra(EXTRA_MIME, selection.sourceMime)
                putExtra(EXTRA_FORMAT, selection.outputFormat)
                putExtra(EXTRA_MP3_BITRATE, selection.mp3BitrateKbps ?: -1)
                putExtra(EXTRA_LABEL, selection.label)
                putExtra(EXTRA_PAGE_URL, selection.pageUrl)
                putExtra(EXTRA_YTDLP_SELECTOR, selection.ytDlpSelector)
                putExtra(EXTRA_SOURCE, info.source)
                putExtra(EXTRA_DURATION, info.durationSec)
                putExtra(EXTRA_IS_IMAGE, selection.isImage)
            }
            startForeground(context, intent)
        }

        /** Pauses a running download, or parks a queued one. */
        fun pause(context: Context, taskId: String) {
            val service = instance
            if (service != null && service.currentTask?.id == taskId) {
                service.pauseRequested = true
            } else {
                VideoDownloadStore.update(taskId) { it.copy(status = VideoDownloadStatus.PAUSED, stage = "Paused · tap to resume") }
            }
        }

        /** Resumes a paused download, continuing from the bytes already on disk. */
        fun resume(context: Context, taskId: String) {
            val task = VideoDownloadStore.find(taskId) ?: return
            val selection = task.selection ?: return
            VideoDownloadStore.update(taskId) { it.copy(status = VideoDownloadStatus.QUEUED, stage = "Resuming…") }
            val service = instance
            if (service != null) {
                service.enqueue(task.copy(status = VideoDownloadStatus.QUEUED), selection, resume = true)
            } else {
                val intent = Intent(context, VideoDownloadService::class.java).apply {
                    setAction(ACTION_RESUME)
                    putExtra(EXTRA_ID, taskId)
                }
                startForeground(context, intent)
            }
        }

        /** Stops the download and forgets it, partial file and all. */
        fun delete(context: Context, taskId: String) {
            val task = VideoDownloadStore.find(taskId)
            val service = instance
            if (service != null && service.currentTask?.id == taskId) {
                service.cancelled = true
                service.discardWorkDir = true
            }
            task?.workDir?.let { runCatching { File(it).deleteRecursively() } }
            VideoDownloadStore.remove(taskId)
        }

        fun cancel(context: Context) {
            val service = instance
            if (service != null) {
                service.cancelled = true
                service.discardWorkDir = true
            } else {
                runCatching { context.startService(Intent(context, VideoDownloadService::class.java).setAction(ACTION_CANCEL)) }
            }
        }

        private fun startForeground(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure {
                // If the system refuses to start us, say so instead of dying silently.
                CrashLog.record(context, "Could not start the download service", it)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<Pending>()
    private var worker: Job? = null

    @Volatile private var cancelled = false
    @Volatile private var pauseRequested = false
    @Volatile private var discardWorkDir = false
    @Volatile private var currentTask: VideoDownloadTask? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(applicationContext)
        createChannel()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return handleCommand(intent)
        } catch (t: Throwable) {
            CrashLog.record(this, "Download service failed to start", t)
            // Enter the foreground before bowing out, for the same reason as above.
            runCatching { startForegroundCompat(buildIdleNotification()) }
            runCatching { stopForegroundCompat() }
            runCatching { stopSelf() }
            return START_NOT_STICKY
        }
    }

    private fun handleCommand(intent: Intent?): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            discardWorkDir = true
            if (pending.isEmpty() && worker?.isActive != true) {
                runCatching { stopForegroundCompat() }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_RESUME) {
            val id = intent.getStringExtra(EXTRA_ID)
            val task = id?.let { VideoDownloadStore.find(it) }
            val selection = task?.selection
            if (task == null || selection == null) {
                enterForegroundAndStop()
                return START_NOT_STICKY
            }
            enqueue(task, selection, resume = true)
            return START_NOT_STICKY
        }

        val taskId = intent?.getStringExtra(EXTRA_ID)
        val format = intent?.getStringExtra(EXTRA_FORMAT) ?: "mp4"
        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL)
        val audioUrl = intent?.getStringExtra(EXTRA_AUDIO_URL)
        val ytDlpSelector = intent?.getStringExtra(EXTRA_YTDLP_SELECTOR)
        // A yt-dlp job has neither a video nor an audio URL — yt-dlp fetches the file
        // itself — so it must not be treated as an empty request.
        if (taskId == null || (videoUrl == null && audioUrl == null && ytDlpSelector == null)) {
            enterForegroundAndStop()
            return START_NOT_STICKY
        }

        val task = VideoDownloadTask(
            id = taskId,
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Video",
            label = intent.getStringExtra(EXTRA_LABEL) ?: "",
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMB),
            source = intent.getStringExtra(EXTRA_SOURCE),
            durationSec = intent.getLongExtra(EXTRA_DURATION, 0L)
        )
        val selection = DownloadSelection(
            label = task.label,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            sourceMime = intent.getStringExtra(EXTRA_MIME) ?: "video/mp4",
            outputFormat = format,
            mp3BitrateKbps = intent.getIntExtra(EXTRA_MP3_BITRATE, -1).takeIf { it > 0 },
            pageUrl = intent.getStringExtra(EXTRA_PAGE_URL),
            ytDlpSelector = ytDlpSelector,
            isImage = intent.getBooleanExtra(EXTRA_IS_IMAGE, false)
        )
        VideoDownloadStore.add(task)
        enqueue(task, selection, resume = false)
        return START_NOT_STICKY
    }

    private fun enqueue(task: VideoDownloadTask, selection: DownloadSelection, resume: Boolean) {
        pending.addLast(Pending(task, selection, resume))
        // getForegroundServiceType must be answered promptly, so post the notification first.
        startForegroundCompat(buildNotification(task, selection, resume))
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
    }

    /**
     * The system starts this service with `startForegroundService`, and it kills the
     * whole app if the service returns without ever entering the foreground. So even
     * the "nothing to do" paths have to call `startForeground` once before stopping.
     */
    private fun enterForegroundAndStop() {
        if (pending.isEmpty() && worker?.isActive != true) {
            startForegroundCompat(buildIdleNotification())
            runCatching { stopForegroundCompat() }
            stopSelf()
        }
    }

    private fun buildIdleNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Off")
            .setContentText("Nothing to download")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private suspend fun drainQueue() {
        while (true) {
            val item = pending.removeFirstOrNull() ?: break
            try {
                runDownload(item)
            } catch (t: Throwable) {
                // Nothing may escape here: an uncaught throw on this thread would kill the app.
                CrashLog.record(this, "Download crashed", t)
                VideoDownloadStore.update(item.task.id) {
                    it.copy(status = VideoDownloadStatus.FAILED, message = t.message ?: "Download failed.")
                }
            }
        }
        runCatching { stopForegroundCompat() }
        stopSelf()
    }

    private fun runDownload(item: Pending) {
        val task = item.task
        val selection = item.selection
        currentTask = task
        cancelled = false
        pauseRequested = false
        discardWorkDir = false

        val workDir = task.workDir?.let { File(it) }?.takeIf { it.isDirectory }
            ?: File(cacheDir, "off_video_${System.currentTimeMillis()}")
        workDir.mkdirs()

        VideoDownloadStore.update(task.id) {
            it.copy(
                status = VideoDownloadStatus.RUNNING,
                stage = if (item.resume) "Resuming…" else "Starting…",
                workDir = workDir.absolutePath,
                selection = selection
            )
        }

        var outputUri: Uri? = null
        var legacyFile: File? = null
        var keepWorkDir = false
        try {
            val finalFile: File = when {
                // yt-dlp fetches the file itself: the streams often need headers or
                // cookies only the extractor knows about.
                selection.ytDlpSelector != null -> downloadViaYtDlp(workDir, selection, task)
                selection.isAudio -> downloadAudio(workDir, selection, task)
                else -> downloadVideo(workDir, selection, task)
            }

            updateProgress(task, selection, 0.97f, "Saving")

            val extension = finalFile.extension.ifBlank {
                when {
                    selection.isImage -> "jpg"
                    selection.isAudio -> "m4a"
                    else -> "mp4"
                }
            }
            val mime = when {
                selection.isImage -> when (extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
                extension == "mp3" -> "audio/mpeg"
                extension == "m4a" || extension == "aac" -> "audio/mp4"
                extension == "webm" -> if (selection.isAudio) "audio/webm" else "video/webm"
                extension == "mkv" -> "video/x-matroska"
                else -> if (selection.isAudio) "audio/mp4" else "video/mp4"
            }
            val name = buildFileName(task.title, extension)
            val isAudio = mime.startsWith("audio/")
            val isImage = mime.startsWith("image/")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = when {
                    isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val folder = when {
                    isImage -> Environment.DIRECTORY_PICTURES
                    isAudio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_MOVIES
                }
                val relative = "$folder/Off"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(collection, values)
                    ?: error("Android could not create the file.")
                outputUri = uri
                contentResolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Could not open the destination." }
                    finalFile.inputStream().use { input -> input.copyTo(output) }
                }
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } else {
                val dirName = when {
                    isImage -> Environment.DIRECTORY_PICTURES
                    isAudio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_MOVIES
                }
                val dir = getExternalFilesDir(dirName) ?: filesDir
                val dest = File(dir, name)
                finalFile.inputStream().use { input -> FileOutputStream(dest).use { input.copyTo(it) } }
                legacyFile = dest
            }

            val where = when {
                isImage -> "Pictures/Off"
                isAudio -> "Music/Off"
                else -> "Movies/Off"
            }
            VideoDownloadStore.update(task.id) {
                it.copy(
                    status = VideoDownloadStatus.COMPLETED,
                    progress = 1f,
                    stage = "Saved to $where",
                    savedName = name,
                    savedUri = outputUri?.toString() ?: legacyFile?.absolutePath,
                    sizeBytes = finalFile.length(),
                    workDir = null,
                    selection = null
                )
            }
            notify(buildDone(task.title, name, isAudio))
        } catch (paused: DownloadPaused) {
            // Keep the half-finished file so resuming continues instead of restarting.
            keepWorkDir = true
            VideoDownloadStore.update(task.id) {
                it.copy(
                    status = VideoDownloadStatus.PAUSED,
                    stage = "Paused · tap to resume",
                    workDir = workDir.absolutePath,
                    selection = selection
                )
            }
            notify(buildPaused(task.title))
        } catch (t: Throwable) {
            outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            legacyFile?.let { runCatching { it.delete() } }
            val wasCancelled = cancelled || discardWorkDir
            CrashLog.record(this, "Download failed: ${task.title}", t)
            VideoDownloadStore.update(task.id) {
                it.copy(
                    status = VideoDownloadStatus.FAILED,
                    message = if (wasCancelled) "Cancelled" else (t.message ?: "Download failed.")
                )
            }
            notify(buildFailed(task.title, if (wasCancelled) "Cancelled" else t.message, selection.isAudio))
        } finally {
            if (!keepWorkDir) runCatching { workDir.deleteRecursively() }
            currentTask = null
            cancelled = false
            pauseRequested = false
            discardWorkDir = false
        }
    }

    /** Video-only rendition: pair it with the audio and join the two. */
    private fun downloadVideo(workDir: File, selection: DownloadSelection, task: VideoDownloadTask): File {
        val videoUrl = selection.videoUrl ?: error("No video stream to download.")
        val ext = if (selection.sourceMime.contains("webm")) "webm" else "mp4"
        val videoFile = File(workDir, "video.$ext")
        downloadToFile(videoUrl, videoFile, task, selection, 0f, 0.70f, "Downloading video")

        val audioUrl = selection.audioUrl
        if (audioUrl.isNullOrBlank()) return videoFile

        checkPause()
        val audioFile = File(workDir, "audio.m4a")
        downloadToFile(audioUrl, audioFile, task, selection, 0.70f, 0.88f, "Downloading audio")
        return mux(videoFile, audioFile, workDir, ext, task, selection)
    }

    /** Audio-only: save the original, or re-encode to MP3. */
    private fun downloadAudio(workDir: File, selection: DownloadSelection, task: VideoDownloadTask): File {
        val audioUrl = selection.audioUrl ?: selection.videoUrl ?: error("No audio stream to download.")
        val sourceExt = when {
            selection.sourceMime.contains("webm") -> "webm"
            selection.sourceMime.contains("mpeg") -> "mp3"
            else -> "m4a"
        }
        val source = File(workDir, "audio.$sourceExt")
        downloadToFile(audioUrl, source, task, selection, 0f, 0.82f, "Downloading audio")

        if (selection.outputFormat != "mp3") return source
        checkPause()
        return transcodeToMp3(source, workDir, selection.mp3BitrateKbps ?: 320, task, selection)
    }

    /** Re-encodes a downloaded audio file to MP3 with the bundled FFmpeg. */
    private fun transcodeToMp3(
        source: File,
        workDir: File,
        bitrate: Int,
        task: VideoDownloadTask,
        selection: DownloadSelection
    ): File {
        updateProgress(task, selection, 0.90f, "Converting to MP3")
        val mp3 = File(workDir, "off_converted.mp3")
        val ok = YtDlpEngine.runFfmpeg(
            this,
            listOf(
                "-y", "-i", source.absolutePath,
                "-vn", "-codec:a", "libmp3lame", "-b:a", "${bitrate}k",
                "-f", "mp3", mp3.absolutePath
            )
        )
        if (!ok || !mp3.exists() || mp3.length() == 0L) {
            // The bundled ffmpeg is a minimal build; if this one cannot encode MP3,
            // hand back the original audio rather than failing the download.
            return source
        }
        return mp3
    }

    /**
     * Runs yt-dlp on a worker thread and polls its progress file, so the same
     * notification and the same download list show live progress either way.
     */
    private fun downloadViaYtDlp(
        workDir: File,
        selection: DownloadSelection,
        task: VideoDownloadTask
    ): File {
        val pageUrl = selection.pageUrl ?: error("The original link is missing.")
        val selector = selection.ytDlpSelector ?: error("No format was chosen.")

        YtDlpEngine.prepare(this)
        YtDlpEngine.cleanup(this, task.id)

        val factory = ThreadFactory { runnable -> Thread(runnable, "off-ytdlp-${task.id.take(8)}") }
        val executor = Executors.newSingleThreadExecutor(factory)
        // The body is wrapped so nothing can escape on the worker thread: an uncaught
        // throw there would take the whole app down instead of failing one download.
        val future = executor.submit(
            Callable {
                runCatching {
                    YtDlpEngine.startDownload(this, pageUrl, selector, workDir, task.id)
                }.onFailure { CrashLog.record(this, "yt-dlp download failed", it) }.getOrDefault(false)
            }
        )
        try {
            while (!future.isDone) {
                if (cancelled || discardWorkDir) YtDlpEngine.cancel(this, task.id)
                if (pauseRequested) YtDlpEngine.cancel(this, task.id)
                YtDlpEngine.progress(this, task.id)?.let { p ->
                    val stage = when (p.status) {
                        "finished" -> "Finishing"
                        "downloading" -> "Downloading"
                        "processing" -> "Processing"
                        "error" -> "Failed"
                        else -> p.message.ifBlank { "Downloading" }
                    }
                    val speed = if (p.speedBytesPerSecond > 0) " · ${formatBytes(p.speedBytesPerSecond)}/s" else ""
                    val eta = p.etaSeconds?.let { " · ${it}s left" } ?: ""
                    updateProgress(
                        task,
                        selection,
                        (p.percent / 100f).coerceIn(0f, 1f),
                        "$stage · ${p.percent.toInt()}%$speed$eta"
                    )
                }
                Thread.sleep(400)
            }
            val ok = runCatching { future.get() }.getOrDefault(false)
            if (pauseRequested) throw DownloadPaused()
            if (!ok) error(if (cancelled || discardWorkDir) "Cancelled" else "The download did not complete.")
        } finally {
            runCatching { executor.shutdownNow() }
            runCatching { YtDlpEngine.cleanup(this, task.id) }
        }

        // yt-dlp names its own files, and a merged result is the largest file that
        // is not one of the intermediate .fNNN parts.
        val produced = workDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && !INTERMEDIATE_PART.containsMatchIn(it.name) }
            ?.maxByOrNull { it.length() }
            ?: error("The download finished but produced no file.")

        if (selection.outputFormat == "mp3") {
            checkPause()
            return transcodeToMp3(produced, workDir, selection.mp3BitrateKbps ?: 320, task, selection)
        }
        return produced
    }

    private fun checkPause() {
        if (pauseRequested) throw DownloadPaused()
    }

    private fun downloadToFile(
        url: String,
        target: File,
        task: VideoDownloadTask,
        selection: DownloadSelection,
        from: Float,
        to: Float,
        stage: String
    ) {
        // Resuming: ask the server for the rest of the file instead of starting over.
        val existing = if (target.exists()) target.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Off-Android/4.1.0")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) error("Server returned HTTP $code")

            val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (existing > 0L && !append) runCatching { target.delete() }
            val already = if (append) existing else 0L
            val length = connection.contentLengthLong
            val total = if (append && length > 0) existing + length else length

            connection.inputStream.use { input ->
                FileOutputStream(target, append).use { output ->
                    copyWithProgress(input, output, total, already, task, selection, from, to, stage)
                }
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        total: Long,
        alreadyDone: Long,
        task: VideoDownloadTask,
        selection: DownloadSelection,
        from: Float,
        to: Float,
        stage: String
    ) {
        val buffer = ByteArray(1024 * 1024)
        var read = input.read(buffer)
        var done = alreadyDone
        var lastStep = -1
        while (read >= 0) {
            if (cancelled || discardWorkDir) error("Cancelled")
            if (pauseRequested) throw DownloadPaused()
            output.write(buffer, 0, read)
            done += read
            if (total > 0) {
                val fraction = from + (to - from) * (done.toFloat() / total.toFloat())
                val step = (fraction * 100).toInt()
                if (step != lastStep) {
                    lastStep = step
                    updateProgress(task, selection, fraction.coerceIn(0f, 1f), "$stage · $step%")
                }
            } else {
                updateProgress(task, selection, null, "$stage · ${formatBytes(done)}")
            }
            read = input.read(buffer)
        }
        output.flush()
    }

    /**
     * Joins video and audio and returns the finished file. Tries a stream copy
     * first; if the containers disagree, re-encodes only the audio into an MP4.
     */
    private fun mux(
        video: File,
        audio: File,
        workDir: File,
        videoExt: String,
        task: VideoDownloadTask,
        selection: DownloadSelection
    ): File {
        updateProgress(task, selection, 0.90f, "Joining video and audio")
        val container = if (videoExt == "webm") "webm" else "mp4"
        val copied = File(workDir, "joined.$container")

        val copy = YtDlpEngine.runFfmpeg(
            this,
            listOf(
                "-y", "-i", video.absolutePath, "-i", audio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-shortest", copied.absolutePath
            )
        )
        if (copy && copied.exists() && copied.length() > 0L) {
            return copied
        }

        val reencoded = File(workDir, "joined-aac.mp4")
        val reencode = YtDlpEngine.runFfmpeg(
            this,
            listOf(
                "-y", "-i", video.absolutePath, "-i", audio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-shortest", reencoded.absolutePath
            )
        )
        if (reencode && reencoded.exists() && reencoded.length() > 0L) {
            return reencoded
        }

        error("Could not join the video and audio streams. Try a different quality.")
    }

    private fun updateProgress(task: VideoDownloadTask, selection: DownloadSelection, progress: Float?, stage: String) {
        VideoDownloadStore.update(task.id) { it.copy(progress = progress, stage = stage) }
        notify(buildNotification(task.copy(progress = progress, stage = stage), selection, false))
    }

    /* ------------------------------- notifications ------------------------------ */

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Video downloads", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progress for downloads started in Off" }
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(task: VideoDownloadTask, selection: DownloadSelection, resume: Boolean): Notification {
        val percent = ((task.progress ?: 0f) * 100).toInt().coerceIn(0, 100)
        val cancel = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Cancel",
            PendingIntent.getService(
                this, 9,
                Intent(this, VideoDownloadService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.title)
            .setContentText(task.stage ?: if (resume) "Resuming…" else "Preparing…")
            .setSubText(if (selection.isAudio) "Off · audio" else "Off · video")
            .setProgress(100, percent, task.progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(cancel)
            .build()
    }

    private fun buildDone(title: String, name: String, isAudio: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Saved: $title")
            .setContentText("${if (isAudio) "Audio" else "Video"} complete · $name")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun buildPaused(title: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Paused: $title")
            .setContentText("Tap resume in Off to continue")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun buildFailed(title: String, reason: String?, isAudio: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(
                if (reason == "Cancelled") "Download cancelled"
                else if (isAudio) "Audio download failed" else "Video download failed"
            )
            .setContentText("$title — ${reason ?: "unknown error"}")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun notify(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    /**
     * Entering the foreground is what keeps the system from killing us, so a failure
     * here must not be swallowed. If the typed call is refused, retry without a type;
     * if that also fails, record it and give up cleanly.
     */
    private fun startForegroundCompat(notification: Notification) {
        val typed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        val first = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, typed) }
        if (first.isSuccess) return
        val second = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0) }
        if (second.isSuccess) return
        CrashLog.record(this, "Could not enter the foreground for a download", first.exceptionOrNull())
        runCatching { stopSelf() }
    }

    private fun stopForegroundCompat() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    private fun buildFileName(title: String, extension: String): String {
        val base = title
            .replace(Regex("[^A-Za-z0-9 ._()\\-]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(90)
            .ifBlank { "Off Audio" }
        return "$base.$extension"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
