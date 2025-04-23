package com.example.scoreviewer

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Page

class PdfManager {
    private var doc: Document? = null

    /** 기존 문서가 열려 있으면 닫고, 새 파일을 연다 */
    fun open(path: String) {
        doc?.destroy()
        doc = Document.openDocument(path)
    }

    /** 문서를 닫고 레퍼런스 해제 */
    fun close() {
        doc?.destroy()
        doc = null
    }

    /** 전체 페이지 수 */
    fun pageCount(): Int = doc?.countPages() ?: 0

    /** 지정 인덱스의 페이지 객체 반환 */
    fun loadPage(index: Int): Page = doc!!.loadPage(index)
}
