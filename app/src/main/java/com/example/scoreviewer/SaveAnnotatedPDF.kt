package com.example.scoreviewer

import android.graphics.Bitmap
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
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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

        // PdfDocument 생성
        val pdf = PdfDocument()
        val pageCount = pdfManager.pageCount()  // :contentReference[oaicite:0]{index=0}

        for (i in 0 until pageCount) {
            annotationView.setPage(i)
            // 페이지 로드 및 비트맵 변환
            val page: Page = pdfManager.loadPage(i)         // :contentReference[oaicite:1]{index=1}
            val pix = page.toPixmap(                      // toPixmap or render 선택 가능
                Matrix.Scale(1.0f),
                ColorSpace.DeviceRGB,
                true, true
            )
            // 1) raw 배열 가져오기
            val raw = pix.pixels
            // 2) ABGR → ARGB로 R/B 채널 스왑
            for (i in raw.indices) {
                val px = raw[i]
                val a = (px ushr 24) and 0xFF
                val b = (px ushr 16) and 0xFF
                val g = (px ushr  8) and 0xFF
                val r = px and 0xFF
                raw[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            // 3) 스왑된 raw로 비트맵 생성
            val bmp = Bitmap.createBitmap(pix.width, pix.height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(raw, 0, pix.width, 0, 0, pix.width, pix.height)

            pix.destroy()
            page.destroy()

            // PDF 페이지 크기에 맞춰 새 페이지 생성
            val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
            val pdfPage = pdf.startPage(info)
            // 원본 비트맵 그리기
            pdfPage.canvas.drawBitmap(bmp, 0f, 0f, null)
            bmp.recycle()

            // AnnotationCanvasView 합성 (에뮬레이터 화면 크기 그대로)
            val scaleX = bmp.width / annotationView.width.toFloat()
            val scaleY = bmp.height / annotationView.height.toFloat()
            pdfPage.canvas.save()
            pdfPage.canvas.scale(scaleX, scaleY)
            annotationView.draw(pdfPage.canvas)
            pdfPage.canvas.restore()

            pdf.finishPage(pdfPage)
        }
        // 파일 쓰기
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()

        // 이제 반환만 함
        return outFile
    }
}
