package com.example.scoreviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import java.io.File
import java.io.FileOutputStream

object SaveAnnotatedPDF {

    fun generateSaveFileName(context: Context, originalFile: File): String {
        // 1) 다운로드 폴더 가져오기
        val downloadsDir = context
            .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
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
        outputName: String,
        overwrite: Boolean = false
    ): File? {
        // 저장 위치 준비 (공용 Download 폴더)
        val downloadsDir = annotationView.context.getExternalFilesDir(
            Environment.DIRECTORY_DOWNLOADS
        )!!
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var baseName = outputName
        var outFile = File(downloadsDir, "$baseName.pdf")

        if (!overwrite && outFile.exists()) {
            val regex = Regex("(.+)_sv(\\d*)$")
            val match = regex.matchEntire(baseName)
            if (match != null) {
                val root = match.groupValues[1]
                var num = match.groupValues[2].ifEmpty { "1" }.toInt() + 1
                do {
                    baseName = "${root}_sv$num"
                    outFile = File(downloadsDir, "$baseName.pdf")
                    num++
                } while (outFile.exists())
            } else {
                baseName = "${baseName}_sv"
                outFile = File(downloadsDir, "$baseName.pdf")
                var num = 2
                while (outFile.exists()) {
                    outFile = File(downloadsDir, "${baseName}$num.pdf")
                    num++
                }
            }
        }
        if (outFile.exists()) outFile.delete()

        val pdf = PdfDocument()
        val pageCount = pdfManager.pageCount()

        try {
            for (i in 0 until pageCount) {
                annotationView.setPage(i)

                // (1) 안전하게 페이지/픽스맵 생성
                val page = try { pdfManager.loadPage(i) } catch (e: Exception) { null }
                if (page == null) throw IllegalStateException("페이지를 불러올 수 없습니다.")

                val pix = try {
                    page.toPixmap(Matrix.Scale(1.0f), ColorSpace.DeviceRGB, true, true)
                } catch (e: Exception) {
                    try { page.destroy() } catch (_: Exception) {}
                    throw IllegalStateException("픽스맵 생성 실패")
                }

                val bmp = try {
                    val raw = pix.pixels
                    for (j in raw.indices) {
                        val px = raw[j]
                        raw[j] = ((px ushr 24) and 0xFF shl 24) or
                                ((px      ) and 0xFF shl 16) or
                                ((px ushr  8) and 0xFF shl  8) or
                                ((px ushr 16) and 0xFF)
                    }
                    Bitmap.createBitmap(pix.width, pix.height, Bitmap.Config.ARGB_8888).apply {
                        setPixels(raw, 0, pix.width, 0, 0, pix.width, pix.height)
                    }
                } catch (e: Exception) {
                    try { pix.destroy() } catch (_: Exception) {}
                    try { page.destroy() } catch (_: Exception) {}
                    throw IllegalStateException("비트맵 생성 실패")
                }
                pix.destroy()
                page.destroy()

                val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
                val pdfPage = pdf.startPage(info)
                pdfPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                bmp.recycle()

                // 필기 좌표 변환 로직(생략 가능)
                val topLeft = annotationView.mapModelToScreen(0f, 0f)
                val topRight = annotationView.mapModelToScreen(bmp.width.toFloat(), 0f)
                val bottomLeft = annotationView.mapModelToScreen(0f, bmp.height.toFloat())
                val dispLeft = topLeft[0]
                val dispTop = topLeft[1]
                val dispWidth = topRight[0] - topLeft[0]
                val dispHeight = bottomLeft[1] - topLeft[1]
                val scaleX = bmp.width / dispWidth
                val scaleY = bmp.height / dispHeight

                pdfPage.canvas.save()
                pdfPage.canvas.translate(-dispLeft * scaleX, -dispTop * scaleY)
                pdfPage.canvas.scale(scaleX, scaleY)
                annotationView.draw(pdfPage.canvas)
                pdfPage.canvas.restore()

                pdf.finishPage(pdfPage)
            }

            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            return outFile
        } catch (e: Exception) {
            // 예외 발생 시 자원/파일 안전하게 정리
            try { pdf.close() } catch (_: Exception) {}
            if (outFile.exists()) outFile.delete()
            e.printStackTrace()
            return null
        }
    }

}
