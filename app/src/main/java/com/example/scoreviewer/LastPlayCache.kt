package com.example.scoreviewer

/** 프로세스 내부에서만 유지되는 마지막 재생 상태 캐시 */
object LastPlayCache {
    var page: Int = -1
    var millis: Int = -1
    var pdfPath: String? = null
    var midiPath: String? = null
    var musicXmlPath: String? = null

    fun clear() {
        page = -1
        millis = -1
        pdfPath = null
        midiPath = null
        musicXmlPath = null
    }
} 