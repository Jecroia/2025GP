package com.example.scoreviewer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.artifex.mupdf.fitz.Document
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private var document: Document? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RecyclerView 생성
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }
        setContentView(recyclerView)

        // PDF 파일 불러오기
        val pdfFile = copyAssetToFile("Chopin_Klavierwerke_Band_2_Peters_Op.31_600dpi.pdf")
        if (pdfFile == null) return

        document = Document.openDocument(pdfFile.absolutePath)

        // 페이지 수 가져오기
        val pageCount = document!!.countPages()

        // 어댑터 연결
        recyclerView.adapter = PdfPageAdapter(document!!, pageCount)
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
        }
        return tempFile
    }
}
