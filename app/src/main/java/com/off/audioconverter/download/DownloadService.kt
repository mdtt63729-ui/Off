package com.off.audioconverter.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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
import com.off.audioconverter.music.ResolvedAudio
import com.off.audioconverter.music.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a resolved audio stream and mirrors progress into the status bar.
 *
 * Runs as a `dataSync` foreground service so the download survives the app being
 * backgrounded, and keeps a determinate progress notification on screen.
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "off_downloads"
        private const val NOTIFICATION_ID = 4711
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

        /** Resolves nothing — the caller must pass an already-resolved stream. */
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

        private fun trackFrom(intent: Intent): Track? {
            val url = intent.getStringExtra(EXTRA_URL) ?: return null
            val sourceName = intent.getStringExtra(EXTRA_SOURCE)
            val source = com.off.audioconverter.music.MusicSource.entries
                .firstOrNull { it.name == sourceName }
                ?: com.off.audioconverter.music.MusicSource.YOUTUBE
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
    private var currentCancel: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_ID)
        val track = intent?.let { trackFrom(it) }
        if (taskId == null || track == null) {
            if (pending.isEmpty() && worker?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        val task = DownloadTask(id = taskId, track = track, status = DownloadStatus.QUEUED)
        DownloadStore.add(task)
        pending.addLast(task)
        // Enter the foreground immediately so the system does not time us out.
        startForegroundCompat(buildNotification(task, indeterminate = true))
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() {
        while (true) {
            val task = pending.removeFirstOrNull() ?: break
            runDownload(task)
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun runDownload(task: DownloadTask) {
        DownloadStore.update(task.id) { it.copy(status = DownloadStatus.RUNNING, message = "Starting…") }
        notify(buildNotification(task.copy(status = DownloadStatus.RUNNING), indeterminate = true))

        var connection: HttpURLConnection? = null
        var outputUri: Uri? = null
        var legacyFile: File? = null
        try {
            connection = (URL(task.track.streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Off-Android/3.0")
            }
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) error("Server returned HTTP $code")
            val total = connection.contentLengthLong

            val name = buildFileName(task.track)
            val resolver = contentResolver
            val target: Any = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                (target as java.io.OutputStream).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = input.read(buffer)
                    var done = 0L
                    var lastPercent = -1
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(task, done, total, percent)
                            }
                        } else {
                            onProgress(task, done, -1L, -1)
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
            notify(buildCompleted(task.copy(status = DownloadStatus.COMPLETED, savedName = name)))
        } catch (e: Exception) {
            outputUri?.let { runCatching { contentResolver.delete(it, null, null) } }
            legacyFile?.let { runCatching { it.delete() } }
            DownloadStore.update(task.id) {
                it.copy(status = DownloadStatus.FAILED, message = e.message ?: "Download failed.")
            }
            notify(buildFailed(task.copy(status = DownloadStatus.FAILED, message = e.message)))
        } finally {
            runCatching { connection?.disconnect() }
            currentCancel = null
        }
    }

    private fun onProgress(task: DownloadTask, done: Long, total: Long, percent: Int) {
        val progress = if (total > 0) done.toFloat() / total.toFloat() else null
        DownloadStore.update(task.id) {
            it.copy(
                status = DownloadStatus.RUNNING,
                downloadedBytes = done,
                totalBytes = total,
                progress = progress,
                message = if (percent >= 0) "$percent%" else formatBytes(done)
            )
        }
        notify(buildNotification(task.copy(downloadedBytes = done, totalBytes = total, progress = progress), indeterminate = total <= 0))
    }

    /* ------------------------------- notifications ------------------------------ */

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Progress for downloads started in Off" }
        manager.createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(task: DownloadTask, indeterminate: Boolean): Notification {
        val percent = if (task.totalBytes > 0) ((task.downloadedBytes * 100) / task.totalBytes).toInt().coerceIn(0, 100) else 0
        val detail = if (task.totalBytes > 0) {
            "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
        } else {
            formatBytes(task.downloadedBytes)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.track.title)
            .setContentText(detail)
            .setSubText(task.track.artist.ifBlank { "Off" })
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildCompleted(task: DownloadTask): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Saved: ${task.track.title}")
            .setContentText("Download complete · Downloads/Off")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun buildFailed(task: DownloadTask): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText("${task.track.title} — ${task.message ?: "unknown error"}")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    private fun notify(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
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
        val ext = if (track.mimeType?.contains("mp4") == true) "m4a"
        else if (track.mimeType?.contains("webm") == true) "webm"
        else "mp3"
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
