package com.off.audioconverter.library

import android.content.Context
import android.content.SharedPreferences
import com.off.audioconverter.music.Track
import com.off.audioconverter.music.TrackJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val name: String, val tracks: List<Track>)

/**
 * Favourites and playlists, persisted as JSON in SharedPreferences so they
 * survive a restart without pulling in a database.
 */
object LibraryStore {

    private const val PREFS = "off_library"
    private const val KEY_FAVOURITES = "favourites"
    private const val KEY_PLAYLISTS = "playlists"

    private var prefs: SharedPreferences? = null

    private val _favourites = MutableStateFlow<List<Track>>(emptyList())
    val favourites: StateFlow<List<Track>> = _favourites.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _favourites.value = TrackJson.listFromJson(store.getString(KEY_FAVOURITES, null))
        _playlists.value = readPlaylists(store.getString(KEY_PLAYLISTS, null))
    }

    /* ------------------------------- favourites ------------------------------- */

    fun isFavourite(trackId: String): Boolean = _favourites.value.any { it.id == trackId }

    fun toggleFavourite(track: Track) {
        val current = _favourites.value
        _favourites.value = if (current.any { it.id == track.id }) {
            current.filterNot { it.id == track.id }
        } else {
            listOf(track) + current
        }
        persistFavourites()
    }

    /* -------------------------------- playlists ------------------------------- */

    fun createPlaylist(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        if (_playlists.value.any { it.name.equals(clean, ignoreCase = true) }) return false
        _playlists.value = _playlists.value + Playlist(clean, emptyList())
        persistPlaylists()
        return true
    }

    fun addToPlaylist(name: String, track: Track): Boolean {
        val list = _playlists.value.toMutableList()
        val index = list.indexOfFirst { it.name == name }
        if (index < 0) return false
        val playlist = list[index]
        if (playlist.tracks.any { it.id == track.id }) return false
        list[index] = playlist.copy(tracks = playlist.tracks + track)
        _playlists.value = list
        persistPlaylists()
        return true
    }

    fun removeFromPlaylist(name: String, trackId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.name == name) playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
            else playlist
        }
        persistPlaylists()
    }

    fun deletePlaylist(name: String) {
        _playlists.value = _playlists.value.filterNot { it.name == name }
        persistPlaylists()
    }

    /* ------------------------------- persistence ------------------------------ */

    private fun persistFavourites() {
        prefs?.edit()?.putString(KEY_FAVOURITES, TrackJson.listToJson(_favourites.value))?.apply()
    }

    private fun persistPlaylists() {
        val array = JSONArray()
        _playlists.value.forEach { playlist ->
            array.put(
                JSONObject().apply {
                    put("name", playlist.name)
                    put("tracks", JSONArray(TrackJson.listToJson(playlist.tracks)))
                }
            )
        }
        prefs?.edit()?.putString(KEY_PLAYLISTS, array.toString())?.apply()
    }

    private fun readPlaylists(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    if (name.isBlank()) continue
                    val tracks = TrackJson.listFromJson(item.optJSONArray("tracks")?.toString())
                    add(Playlist(name, tracks))
                }
            }
        }.getOrDefault(emptyList())
    }
}
