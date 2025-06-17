package com.example.scoreviewer

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Page
import com.example.scoreviewer.MuPdfDispatcher
import kotlinx.coroutines.runBlocking

class PdfManager {
    private var doc: Document? = null

    /** 문서 페이지 수를 캐싱하여 메인 스레드 호출 시 MuPDF 네이티브 컨텍스트 접근을 피한다 */
    private var numPages: Int = 0

    /**
     * 기존 문서를 닫은 뒤 새 파일을 연다.
     * MuPDF 컨텍스트는 생성된 스레드에서만 안전하므로 MuPdfDispatcher 싱글 스레드에서 실행한다.
     */
    fun open(path: String) {
        runBlocking(MuPdfDispatcher.dispatcher) {
            doc?.destroy()
            doc = Document.openDocument(path)
            numPages = doc?.countPages() ?: 0
        }
    }

    fun getDocument(): Document = doc ?: error("문서가 열려있지 않습니다.")

    /** 문서를 닫고 레퍼런스 해제 */
    fun close() {
        runBlocking(MuPdfDispatcher.dispatcher) {
            doc?.destroy()
            doc = null
            numPages = 0
        }
    }

    /** 전체 페이지 수 (캐시값 사용) */
    fun pageCount(): Int = numPages

    /** 지정 인덱스의 페이지 객체 반환 – MuPdfDispatcher 안에서 호출해야 함 */
    fun loadPage(index: Int): Page = doc!!.loadPage(index)
}
