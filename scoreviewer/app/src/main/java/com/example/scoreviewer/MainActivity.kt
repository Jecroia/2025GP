package com.example.scoreviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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

    private val PICK_PDF_FILE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RecyclerView 생성 및 화면에 표시
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }
        setContentView(recyclerView)

        // 앱 시작 시 PDF 파일 선택 요청
        openFilePicker()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_PDF_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val file = copyUriToTempFile(uri)
                openPdf(file)
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("selected_pdf", ".pdf", cacheDir)
            tempFile.outputStream().use { output ->
                inputStream?.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openPdf(pdfFile: File?) {
        if (pdfFile == null) {
            Log.e("PDFViewer", "파일이 null입니다.")
            return
        }

        document?.destroy() // 기존 문서 있으면 정리
        document = Document.openDocument(pdfFile.absolutePath)

        val pageCount = document!!.countPages()
        recyclerView.adapter = PdfPageAdapter(document!!, pageCount)
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}