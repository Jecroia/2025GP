package com.example.scoreviewer

data class Bookmark(
    val page: Int,        // 0-based 페이지 인덱스
    var comment: String   // 코멘트 텍스트
)