package com.example.scoreviewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import java.io.File
import java.io.FileOutputStream

object SaveAnnotatedPDF {

    fun generateSaveFileName(originalFile: File): String {
        // 1) 다운로드 폴더 가져오기
        val downloadsDir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        // 2) 파일명에서 _svN 접미사를 제거해 "루트" 이름만 뽑기
        val root = originalFile.nameWithoutExtension
            .replace(Regex("_sv(\\d+)?$"), "")

        // 3) 다운로드 폴더 내에서 root_sv, root_sv2, root_sv3… 와 매칭되는 파일들 검색
        val pattern = Regex("^${Regex.escape(root)}_sv(\\d+)?\$")
        var maxIndex = 0
        downloadsDir.listFiles { f ->
            f.extension.equals("pdf", ignoreCase = true)
                    && pattern.matches(f.nameWithoutExtension)
        }?.forEach { f ->
            val suffix = f.nameWithoutExtension.removePrefix("${root}_sv")
            // suffix가 빈 문자열이면 index=1, 아니면 parseInt
            val idx = suffix.toIntOrNull() ?: 1
            if (idx > maxIndex) maxIndex = idx
        }

        // 4) 다음 인덱스 계산
        val next = maxIndex + 1
        return if (next == 1) "${root}_sv" else "${root}_sv$next"
    }


    fun save(
        pdfManager: PdfManager,
        annotationView: AnnotationCanvasView,
        outputName: String
    ): File {
        // 저장 위치 준비 (공용 Download 폴더)
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var baseName = outputName
        var outFile = File(downloadsDir, "$baseName.pdf")

        if (outFile.exists()) {
            // outputName이 이미 "_sv" 또는 "_svN" 으로 끝나는지 확인
            val regex = Regex("(.+)_sv(\\d*)$")
            val match = regex.matchEntire(baseName)
            if (match != null) {
                // 이미 _sv 또는 _svN 이 붙어 있는 경우
                val root = match.groupValues[1]
                // 기존 숫자 뒤에 +1, 없으면 2부터
                var num = match.groupValues[2].ifEmpty { "1" }.toInt() + 1
                do {
                    baseName = "${root}_sv$num"
                    outFile = File(downloadsDir, "$baseName.pdf")
                    num++
                } while (outFile.exists())
            } else {
                // _sv 가 붙지 않은 경우
                baseName = "${baseName}_sv"
                outFile = File(downloadsDir, "$baseName.pdf")
                var num = 2
                while (outFile.exists()) {
                    outFile = File(downloadsDir, "${baseName}$num.pdf")
                    num++
                }
            }
        }
        // 이미 남아 있던 파일 삭제
        if (outFile.exists()) outFile.delete()

        val pdf = PdfDocument()
        val pageCount = pdfManager.pageCount()
        for (pageIndex in 0 until pageCount) {
            // 1) muPDF → ARGB bitmap (pageBmp)
            annotationView.setPage(pageIndex)
            val page = pdfManager.loadPage(pageIndex)
            val pix = page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)
            val raw = pix.pixels
            for (j in raw.indices) {
                val px = raw[j]
                raw[j] = ((px ushr 24) and 0xFF shl 24) or  // A
                        (px and 0xFF shl 16) or            // R
                        ((px ushr 8) and 0xFF shl 8) or    // G
                        ((px ushr 16) and 0xFF)            // B
            }
            val pageBmp = Bitmap.createBitmap(pix.width, pix.height, Bitmap.Config.ARGB_8888)
            pageBmp.setPixels(raw, 0, pix.width, 0, 0, pix.width, pix.height)
            pix.destroy(); page.destroy()

            // 2) 이 pageBmp 위에 바로 annotationView 그리기
            //    —> 화면에서 하이라이트와 펜 스트로크가 혼합되던 그 Canvas
            val composite = pageBmp.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(composite).apply {
                // 2-1) scale 매핑 (뷰 좌표 → 페이지 픽셀 좌표)
                val sx = width  / annotationView.width.toFloat()
                val sy = height / annotationView.height.toFloat()
                save()
                scale(sx, sy)
                annotationView.draw(this)    // 이 한 줄이 투명도와 순서를 모두 보장
                restore()
            }

            // 3) PDF 페이지에 composite 하나만 그리기
            val info    = PdfDocument.PageInfo.Builder(composite.width, composite.height, pageIndex+1).create()
            val pdfPage = pdf.startPage(info)
            pdfPage.canvas.drawBitmap(composite, 0f, 0f, null)
            pdf.finishPage(pdfPage)

            composite.recycle()
        }

        // 4) 파일 쓰기
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }
}
