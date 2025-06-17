package com.example.scoreviewer

import android.content.Context
import android.content.SharedPreferences

class PlaybackState(context: Context) {
    companion object {
        private const val PREF_NAME = "PlaybackPrefs"
        private const val KEY_PAGE = "last_page"
        private const val KEY_MILLIS = "last_millis"
        private const val KEY_PDF_PATH = "last_pdf"
        private const val KEY_MIDI_PATH = "last_midi"
        private const val KEY_MUSICXML_PATH = "last_musicxml"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    data class SavedState(
        val page: Int,
        val millis: Int,
        val pdfPath: String?,
        val midiPath: String?,
        val musicXmlPath: String?
    )

    fun saveState(page: Int, millis: Int, pdfPath: String?, midiPath: String?, musicXmlPath: String?) {
        prefs.edit().apply {
            putInt(KEY_PAGE, page)
            putInt(KEY_MILLIS, millis)
            putString(KEY_PDF_PATH, pdfPath)
            putString(KEY_MIDI_PATH, midiPath)
            putString(KEY_MUSICXML_PATH, musicXmlPath)
            apply()
        }
    }

    fun loadState(): SavedState {
        return SavedState(
            page = prefs.getInt(KEY_PAGE, -1),
            millis = prefs.getInt(KEY_MILLIS, -1),
            pdfPath = prefs.getString(KEY_PDF_PATH, null),
            midiPath = prefs.getString(KEY_MIDI_PATH, null),
            musicXmlPath = prefs.getString(KEY_MUSICXML_PATH, null)
        )
    }

    fun shouldRestoreState(): Boolean {
        val savedState = loadState()
        return savedState.page != -1 &&
                savedState.millis != -1 &&
                !(savedState.page == 0 && savedState.millis == 0) &&
                savedState.pdfPath != null &&
                savedState.midiPath != null
    }

    fun clearState() {
        prefs.edit().clear().apply()
    }
} 