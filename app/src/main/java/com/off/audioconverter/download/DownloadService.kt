package com.off.audioconverter.download

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
import com.off.audioconverter.music.MusicSource
import com.off.audioconverter.music.ResolvedAudio
import com.off.audioconverter.ui.CrashLog
import com.off.audioconverter.music.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a resolved audio stream and mirrors progress into the status bar.
 *
 * Runs as a `dataSync` foreground service so the download survives the app being
 * backgrounded. The notification is determinate and carries Pause/Resume and
 * Cancel actions.
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "off_downloads"
        private const val NOTIFICATION_ID = 4711
        const val ACTION_PAUSE = "com.off.audioconverter.download.PAUSE"
        const val ACTION_RESUME = "com.off.audioconverter.download.RESUME"
        const val ACTION_CANCEL = "com.off.audioconverter.download.CANCEL"

        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_URL = "url"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_EXT = "ext"
        private const val EXTRA_ARTWORK = "artwork"
        private const val EXTRA_SOURCE_ID = "sourceId"
        private const val EXTRA_TRACK_ID = "trackId"
        private const val EXTRA_SOURCE = "source"

        /** The caller must pass an already-resolved stream. */
        fun enqueue(context: Context, track: Track, resolved: ResolvedAudio, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_ID, taskId)
                putExtra(EXTRA_TITLE, track.title)
                putExtra(EXTRA_ARTIST, track.artist)
                putExtra(EXTRA_URL, resolved.url)
                putExtra(EXTRA_MIME, resolved.mimeType)
                putExtra(EXTRA_EXT, resolved.extension)
                putExtra(EXTRA_ARTWORK, track.artworkUrl)
                putExtra(EXTRA_SOURCE_ID, track.sourceId)
                putExtra(EXTRA_TRACK_ID, track.id)
                putExtra(EXTRA_SOURCE, track.source.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) = sendAction(context, ACTION_PAUSE)
        fun resume(context: Context) = sendAction(context, ACTION_RESUME)
        fun cancel(context: Context) = sendAction(context, ACTION_CANCEL)

        private fun sendAction(context: Context, action: String) {
            val intent = Intent(context, DownloadService::class.java).setAction(action)
            runCatching { context.startService(intent) }
        }

        private fun trackFrom(intent: Intent): Track? {
            val url = intent.getStringExtra(EXTRA_URL) ?: return null
            val source = MusicSource.entries
                .firstOrNull { it.name == intent.getStringExtra(EXTRA_SOURCE) }
                ?: MusicSource.YOUTUBE
            return Track(
                id = intent.getStringExtra(EXTRA_TRACK_ID) ?: "track:${url.hashCode()}",
                source = source,
                title = intent.getStringExtra(EXTRA_TITLE) ?: "Audio",
                artist = intent.getStringExtra(EXTRA_ARTIST) ?: "",
                artworkUrl = intent.getStringExtra(EXTRA_ARTWORK),
                streamUrl = url,
                sourceId = intent.getStringExtra(EXTRA_SOURCE_ID),
                mimeType = intent.getStringExtra(EXTRA_MIME)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<DownloadTask>()
    private var worker: Job? = null

    @Volatile private var paused = false
    @Volatile private var cancelled = false
    @Volatile private var currentTask: DownloadTask? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                paused = true
                currentTask?.let { task ->
                    DownloadStore.update(task.id) { it.copy(status = DownloadStatus.PAUSED, message = "Paused") }
                    notify(buildNotification(task.copy(status = DownloadStatus.PAUSED, message = "Paused")))
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                paused = false
                currentTask?.let { task ->
                    DownloadStore.update(task.id) { it.copy(status = DownloadStatus.RUNNING, message = "Resuming…") }
                    notify(buildNotification(task.copy(status = DownloadStatus.RUNNING)))
                }
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                cancelled = true
                paused = false
                return START_NOT_STICKY
            }
        }

        val taskId = intent?.getStringExtra(EXTRA_ID)
        val track = intent?.let { trackFrom(it) }
        if (taskId == null || track == null) {
            enterForegroundAndStop()
            return START_NOT_STICKY
        }
        val task = DownloadTask(id = taskId, track = track, status = DownloadStatus.QUEUED)
        DownloadStore.add(task)
        pending.addLast(task)
        startForegroundCompat(buildNotification(task))
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    /**
     * Started with `startForegroundService`, so the foreground obligation has to be
     * met even when there is nothing to do — returning without it makes the system
     * kill the app.
     */
    private fun enterForegroundAndStop() {
        if (pending.isEmpty() && worker?.isActive != true) {
            runCatching { startForegroundCompat(buildIdleNotification()) }
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private suspend fun drainQueue() {
        while (true) {
            val task = pending.removeFirstOrNull() ?: break
            runDownload(task)
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun runDownload(task: DownloadTask) {
        currentTask = task
        paused = false
        cancelled = false
        DownloadStore.update(task.id) { it.copy(status = DownloadStatus.RUNNING, message = "Starting…") }
        notify(buildNotification(task.copy(status = DownloadStatus.RUNNING)))

        var connection: HttpURLConnection? = null
        var outputUri: Uri? = null
        var legacyFile: File? = null
        try {
            connection = (URL(task.track.streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Off-Android/3.1")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) error("Server returned HTTP $code")
            val total = connection.contentLengthLong

            val name = buildFileName(task.track)
            val resolver = contentResolver
            val target: OutputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, task.track.mimeType ?: "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Off")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Android could not create the download file.")
                outputUri = uri
                resolver.openOutputStream(uri, "w") ?: error("Could not open the download file.")
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                val file = File(dir, name)
                legacyFile = file
                FileOutputStream(file)
            }

            connection.inputStream.use { input ->
                target.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = input.read(buffer)
                    var done = 0L
                    var lastPercent = -1
                    while (read >= 0) {
                        if (cancelled) error("Download cancelled")
                        while (paused && !cancelled) Thread.sleep(150)
                        if (cancelled) error("Download cancelled")
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(task, done, total)
                            }
                        } else {
                            onProgress(task, done, -1L)
                        }
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && outputUri != null) {
                resolver.update(
                    outputUri!!,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }

            DownloadStore.update(task.id) {
                it.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 1f,
                    downloadedBytes = if (total > 0) total else it.downloadedBytes,
                    totalBytes = total,
                    savedName = name,
                    savedUri = outputUri?.toString() ?: legacyFile?.absolutePath,
                    message = "Saved to Downloads/Off"
                )
            }
            notify(buildCompleted(task, name))
        } catch (e: Exception) {
            outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            legacyFile?.let { runCatching { it.delete() } }
            val wasCancelled = cancelled
            DownloadStore.update(task.id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    message = if (wasCancelled) "Cancelled" else (e.message ?: "Download failed.")
                )
            }
            notify(buildFailed(task, if (wasCancelled) "Cancelled" else e.message))
        } finally {
            runCatching { connection?.disconnect() }
            currentTask = null
            paused = false
            cancelled = false
        }
    }

    private fun onProgress(task: DownloadTask, done: Long, total: Long) {
        val progress = if (total > 0) done.toFloat() / total.toFloat() else null
        DownloadStore.update(task.id) {
            it.copy(
                status = DownloadStatus.RUNNING,
                downloadedBytes = done,
                totalBytes = total,
                progress = progress,
                message = if (total > 0) "${((done * 100) / total)}%" else formatBytes(done)
            )
        }
        notify(buildNotification(task.copy(downloadedBytes = done, totalBytes = total, progress = progress)))
    }

    /* ------------------------------- notifications ------------------------------ */

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Music downloads", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progress for downloads started in Off" }
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, DownloadService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(task: DownloadTask): Notification {
        val pausedNow = task.status == DownloadStatus.PAUSED
        val percent = if (task.totalBytes > 0) ((task.downloadedBytes * 100) / task.totalBytes).toInt().coerceIn(0, 100) else 0
        val detail = if (task.totalBytes > 0) {
            "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
        } else {
            formatBytes(task.downloadedBytes)
        }
        val toggle = if (pausedNow) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Resume", actionIntent(ACTION_RESUME, 2)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause", actionIntent(ACTION_PAUSE, 1)
            ).build()
        }
        val cancel = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "Cancel", actionIntent(ACTION_CANCEL, 3)
        ).build()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.track.title)
            .setContentText(if (pausedNow) "Paused · $detail" else detail)
            .setSubText(task.track.artist.ifBlank { "Off" })
            .setProgress(100, percent, task.totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(toggle)
            .addAction(cancel)
            .build()
    }

    private fun buildCompleted(task: DownloadTask, name: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Saved: ${task.track.title}")
            .setContentText("Download complete · $name")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun buildFailed(task: DownloadTask, reason: String?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(if (reason == "Cancelled") "Download cancelled" else "Download failed")
            .setContentText("${task.track.title} — ${reason ?: "unknown error"}")
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

    /* --------------------------------- helpers -------------------------------- */

    private fun buildFileName(track: Track): String {
        val base = "${track.title} ${track.artist}"
            .replace(Regex("[^A-Za-z0-9 ._()\\-]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(90)
            .ifBlank { "Off Audio" }
        val ext = when {
            track.mimeType?.contains("mp4") == true -> "m4a"
            track.mimeType?.contains("webm") == true -> "webm"
            else -> "mp3"
        }
        return "$base.$ext"
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
