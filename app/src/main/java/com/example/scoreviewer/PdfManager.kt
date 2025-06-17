package com.example.scoreviewer

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Page

class PdfManager {
    private var doc: Document? = null
    var isClosed = false
    /** 기존 문서가 열려 있으면 닫고, 새 파일을 연다 */
    fun open(path: String) {
        doc?.destroy()
        doc = Document.openDocument(path)
    }
    fun getDocument(): Document = doc ?: error("문서가 열려있지 않습니다.")
    /** 문서를 닫고 레퍼런스 해제 */
    fun close() {
        doc?.destroy()
        doc = null
    }

    /** 전체 페이지 수 */
    fun pageCount(): Int = doc?.countPages() ?: 0

    /** 지정 인덱스의 페이지 객체 반환 */
    fun loadPage(index: Int): Page? {
        // doc이 null이거나 이미 close된 경우 null 반환 (혹은 예외)
        val document = doc
        if (document == null || isClosed) return null
        return try {
            document.loadPage(index)
        } catch (e: Exception) {
            null
        }
    }}
