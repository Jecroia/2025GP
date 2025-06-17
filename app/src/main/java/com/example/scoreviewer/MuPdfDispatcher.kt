package com.example.scoreviewer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext

/**
 * MuPDF 네이티브 라이브러리는 동일 스레드 컨텍스트에서 생성·사용되어야 안전하다.
 * 전역 싱글 스레드 디스패처를 만들어 모든 MuPDF 호출을 이 디스패처에서 실행하도록 강제한다.
 */
object MuPdfDispatcher {
    /** MuPDF 전용 싱글 스레드 */
    val dispatcher: CoroutineDispatcher = newSingleThreadContext("MuPdfThread")
} 