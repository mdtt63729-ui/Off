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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.off.audioconverter.MainActivity
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

/** Observable registry of video downloads, mirroring the music download store. */
object VideoDownloadStore {
    private val _tasks = MutableStateFlow<List<VideoDownloadTask>>(emptyList())
    val tasks: StateFlow<List<VideoDownloadTask>> = _tasks.asStateFlow()

    fun add(task: VideoDownloadTask) {
        _tasks.update { current -> if (current.any { it.id == task.id }) current else listOf(task) + current }
    }

    fun update(id: String, transform: (VideoDownloadTask) -> VideoDownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun isActive(status: VideoDownloadStatus): Boolean =
        status == VideoDownloadStatus.QUEUED || status == VideoDownloadStatus.RUNNING

    fun clearFinished() {
        _tasks.update { list -> list.filter { isActive(it.status) } }
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }
}

/**
 * Downloads a video resolved by [VideoRepository].
 *
 * Runs as a `dataSync` foreground service with a determinate progress
 * notification. Muxed streams are written straight out; video-only renditions
 * are paired with the best audio and joined with FFmpeg (`-c copy`, falling
 * back to re-encoding just the audio if the containers disagree).
 */
class VideoDownloadService : Service() {

    private data class Pending(
        val task: VideoDownloadTask,
        val videoUrl: String,
        val audioUrl: String?,
        val mime: String
    )

    companion object {
        private const val CHANNEL_ID = "off_video_downloads"
        private const val NOTIFICATION_ID = 4720
        const val ACTION_CANCEL = "com.off.audioconverter.video.CANCEL"

        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THUMB = "thumb"
        private const val EXTRA_VIDEO_URL = "videoUrl"
        private const val EXTRA_AUDIO_URL = "audioUrl"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_LABEL = "label"

        fun enqueue(context: Context, info: VideoInfo, quality: VideoQuality, taskId: String) {
            val intent = Intent(context, VideoDownloadService::class.java).apply {
                putExtra(EXTRA_ID, taskId)
                putExtra(EXTRA_TITLE, info.title)
                putExtra(EXTRA_THUMB, info.thumbnailUrl)
                putExtra(EXTRA_VIDEO_URL, quality.videoUrl)
                putExtra(EXTRA_AUDIO_URL, quality.audioUrl)
                putExtra(EXTRA_MIME, quality.mimeType)
                putExtra(EXTRA_LABEL, quality.label)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, VideoDownloadService::class.java).setAction(ACTION_CANCEL)
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<Pending>()
    private var worker: Job? = null

    @Volatile private var cancelled = false
    @Volatile private var currentTask: VideoDownloadTask? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            return START_NOT_STICKY
        }

        val taskId = intent?.getStringExtra(EXTRA_ID)
        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL)
        if (taskId == null || videoUrl == null) {
            if (pending.isEmpty() && worker?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }

        val task = VideoDownloadTask(
            id = taskId,
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Video",
            label = intent.getStringExtra(EXTRA_LABEL) ?: "",
            thumbnailUrl = intent.getStringExtra(EXTRA_THUMB)
        )
        VideoDownloadStore.add(task)
        pending.addLast(
            Pending(
                task = task,
                videoUrl = videoUrl,
                audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL),
                mime = intent.getStringExtra(EXTRA_MIME) ?: "video/mp4"
            )
        )
        startForegroundCompat(buildNotification(task))
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() {
        while (true) {
            val item = pending.removeFirstOrNull() ?: break
            runDownload(item)
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun runDownload(item: Pending) {
        val task = item.task
        currentTask = task
        cancelled = false
        VideoDownloadStore.update(task.id) { it.copy(status = VideoDownloadStatus.RUNNING, stage = "Starting…") }

        val workDir = File(cacheDir, "off_video_${System.currentTimeMillis()}").apply { mkdirs() }
        var outputUri: Uri? = null
        var legacyFile: File? = null
        try {
            val videoExt = if (item.mime.contains("webm")) "webm" else "mp4"
            val videoFile = File(workDir, "video.$videoExt")
            downloadToFile(item.videoUrl, videoFile, task, 0f, 0.70f, "Downloading video")

            var finalFile = videoFile
            if (!item.audioUrl.isNullOrBlank()) {
                val audioFile = File(workDir, "audio.m4a")
                downloadToFile(item.audioUrl, audioFile, task, 0.70f, 0.88f, "Downloading audio")
                finalFile = mux(videoFile, audioFile, workDir, videoExt, task)
            }

            updateProgress(task, 0.97f, "Saving")

            // The container of the finished file decides the name and MIME type.
            val finalExt = finalFile.extension.ifBlank { "mp4" }
            val finalMime = if (finalExt == "webm") "video/webm" else "video/mp4"
            val name = buildFileName(task.title, finalExt)

            val resolver = contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, finalMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Off")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Android could not create the video file.")
                outputUri = uri
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Could not open the destination." }
                    finalFile.inputStream().use { input -> input.copyTo(output) }
                }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
                val dest = File(dir, name)
                finalFile.inputStream().use { input -> FileOutputStream(dest).use { input.copyTo(it) } }
                legacyFile = dest
            }

            VideoDownloadStore.update(task.id) {
                it.copy(
                    status = VideoDownloadStatus.COMPLETED,
                    progress = 1f,
                    stage = "Saved to Movies/Off",
                    savedName = name,
                    savedUri = outputUri?.toString() ?: legacyFile?.absolutePath
                )
            }
            notify(buildDone(task.title, name))
        } catch (e: Exception) {
            outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            legacyFile?.let { runCatching { it.delete() } }
            val wasCancelled = cancelled
            VideoDownloadStore.update(task.id) {
                it.copy(
                    status = VideoDownloadStatus.FAILED,
                    message = if (wasCancelled) "Cancelled" else (e.message ?: "Download failed.")
                )
            }
            notify(buildFailed(task.title, if (wasCancelled) "Cancelled" else e.message))
        } finally {
            workDir.deleteRecursively()
            currentTask = null
            cancelled = false
        }
    }

    private fun downloadToFile(
        url: String,
        target: File,
        task: VideoDownloadTask,
        from: Float,
        to: Float,
        stage: String
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Off-Android/3.4.0")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) error("Server returned HTTP $code")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    copyWithProgress(input, output, total, task, from, to, stage)
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
        task: VideoDownloadTask,
        from: Float,
        to: Float,
        stage: String
    ) {
        val buffer = ByteArray(128 * 1024)
        var read = input.read(buffer)
        var done = 0L
        var lastStep = -1
        while (read >= 0) {
            if (cancelled) error("Download cancelled")
            output.write(buffer, 0, read)
            done += read
            if (total > 0) {
                val fraction = from + (to - from) * (done.toFloat() / total.toFloat())
                val step = (fraction * 100).toInt()
                if (step != lastStep) {
                    lastStep = step
                    updateProgress(task, fraction.coerceIn(0f, 1f), "$stage · $step%")
                }
            } else {
                updateProgress(task, null, "$stage · ${formatBytes(done)}")
            }
            read = input.read(buffer)
        }
        output.flush()
    }

    /**
     * Joins video and audio and returns the finished file. Tries a stream copy
     * first; if the containers disagree, re-encodes only the audio into an MP4.
     */
    private fun mux(video: File, audio: File, workDir: File, videoExt: String, task: VideoDownloadTask): File {
        updateProgress(task, 0.90f, "Joining video and audio")
        val container = if (videoExt == "webm") "webm" else "mp4"
        val copied = File(workDir, "joined.$container")

        val copy = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-i", video.absolutePath, "-i", audio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-shortest", copied.absolutePath
            )
        )
        if (ReturnCode.isSuccess(copy.returnCode) && copied.exists() && copied.length() > 0L) {
            return copied
        }

        val reencoded = File(workDir, "joined-aac.mp4")
        val reencode = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y", "-i", video.absolutePath, "-i", audio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-shortest", reencoded.absolutePath
            )
        )
        if (ReturnCode.isSuccess(reencode.returnCode) && reencoded.exists() && reencoded.length() > 0L) {
            return reencoded
        }

        error("Could not join the video and audio streams. Try a different quality.")
    }

    private fun updateProgress(task: VideoDownloadTask, progress: Float?, stage: String) {
        VideoDownloadStore.update(task.id) { it.copy(progress = progress, stage = stage) }
        notify(buildNotification(task.copy(progress = progress, stage = stage)))
    }

    /* ------------------------------- notifications ------------------------------ */

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Video downloads", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progress for video downloads started in Off" }
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(task: VideoDownloadTask): Notification {
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
            .setContentText(task.stage ?: "Preparing…")
            .setSubText("Off · video")
            .setProgress(100, percent, task.progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(cancel)
            .build()
    }

    private fun buildDone(title: String, name: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Saved: $title")
            .setContentText("Video complete · $name")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun buildFailed(title: String, reason: String?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(if (reason == "Cancelled") "Download cancelled" else "Video download failed")
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

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
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
            .ifBlank { "Off Video" }
        return "$base.$extension"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
