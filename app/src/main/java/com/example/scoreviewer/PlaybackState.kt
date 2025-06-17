package com.example.scoreviewer

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class PlaybackState(context: Context) {
    companion object {
        private const val PREF_NAME = "PlaybackPrefs"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** PDF 경로·크기·수정일 조합으로 고유 ID 생성 */
    private fun pdfId(pdfPath: String): String {
        val f = File(pdfPath)
        return "${f.name}_${f.length()}_${f.lastModified()}"
    }

    data class SavedState(
        val page: Int,
        val millis: Int,
        val midiPath: String?,
        val musicXmlPath: String?
    )

    /** PDF별로 상태 저장 */
    fun saveStateForPdf(
        pdfPath: String,
        page: Int,
        millis: Int,
        midiPath: String?,
        musicXmlPath: String?
    ) {
        val id = pdfId(pdfPath)
        prefs.edit().apply {
            putInt("${id}_page", page)
            putInt("${id}_millis", millis)
            putString("${id}_midiPath", midiPath)
            putString("${id}_musicXmlPath", musicXmlPath)
            apply()
        }
    }

    /** PDF별로 상태 불러오기 */
    fun loadStateForPdf(pdfPath: String): SavedState {
        val id = pdfId(pdfPath)
        return SavedState(
            page         = prefs.getInt("${id}_page", 0),
            millis       = prefs.getInt("${id}_millis", 0),
            midiPath     = prefs.getString("${id}_midiPath", null),
            musicXmlPath = prefs.getString("${id}_musicXmlPath", null)
        )
    }

    /** 현재 PDF에 대해 복원 가능한 상태인지 */
    fun shouldRestoreForPdf(pdfPath: String): Boolean {
        val s = loadStateForPdf(pdfPath)
        return (s.page != 0 || s.millis != 0) && s.midiPath != null
    }

    /** PDF별 저장 내용 삭제할 때 필요하면 호출 */
    fun clearStateForPdf(pdfPath: String) {
        val id = pdfId(pdfPath)
        prefs.edit().apply {
            remove("${id}_page")
            remove("${id}_millis")
            remove("${id}_midiPath")
            remove("${id}_musicXmlPath")
            apply()
        }
    }
}
