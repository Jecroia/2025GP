package com.example.scoreviewer

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var pdfImageView: ImageView
    private var document: Document? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pdfImageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        setContentView(pdfImageView)

        val pdfFile = copyAssetToFile("Second Run.pdf")
        if (pdfFile == null) {
            Log.e("PDFViewer", "파일 복사 실패!")
            return
        }

        try {
            document = Document.openDocument(pdfFile.absolutePath)
            val page: Page? = document?.loadPage(0)
            if (page == null) {
                Log.e("PDFViewer", "페이지 로드 실패!")
                return
            }

            val ctm = Matrix.Scale(1.0f)
            // 투명도를 보존(true)하여 픽스맵 생성 -> 알파 채널 포함됨
            val pixmap: Pixmap? = page.toPixmap(ctm, ColorSpace.DeviceRGB, true, true)
            if (pixmap == null) {
                Log.e("PDFViewer", "픽스맵 생성 실패!")
                return
            }

            val width = pixmap.getWidth()
            val height = pixmap.getHeight()
            Log.d("PDFViewer", "페이지 크기: $width x $height")

            val pixels: IntArray = pixmap.getPixels()  // 이제 RGB/BGR + alpha 채널이 포함됨
            val bitmap: Bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            pdfImageView.setImageBitmap(bitmap)

            page.destroy()
            pixmap.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PDFViewer", "예외 발생: ${e.message}")
        }
    }

    private fun copyAssetToFile(assetName: String): File? {
        var tempFile: File? = null
        try {
            val inputStream: InputStream = assets.open(assetName)
            tempFile = File.createTempFile("temp_pdf", ".pdf", cacheDir)
            tempFile.deleteOnExit()
            val outputStream: OutputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PDFViewer", "파일 복사 중 예외: ${e.message}")
        }
        return tempFile
    }
}
