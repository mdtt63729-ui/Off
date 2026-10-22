package com.off.audioconverter.download

import com.off.audioconverter.music.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

data class DownloadTask(
    val id: String,
    val track: Track,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** 0f..1f, or null when the total size is unknown. */
    val progress: Float? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val message: String? = null,
    val savedName: String? = null,
    val savedUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A tiny in-memory registry of download activity. The UI observes it; the
 * foreground [DownloadService] mutates it.
 */
object DownloadStore {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun add(task: DownloadTask) {
        _tasks.update { current ->
            if (current.any { it.id == task.id }) current else listOf(task) + current
        }
    }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun activeCount(): Int = _tasks.value.count { isActive(it.status) }

    fun isActive(status: DownloadStatus): Boolean =
        status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING || status == DownloadStatus.PAUSED

    fun isDownloaded(trackId: String): Boolean =
        _tasks.value.any { it.track.id == trackId && it.status == DownloadStatus.COMPLETED }

    fun clearFinished() {
        _tasks.update { list -> list.filter { isActive(it.status) } }
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }
}
