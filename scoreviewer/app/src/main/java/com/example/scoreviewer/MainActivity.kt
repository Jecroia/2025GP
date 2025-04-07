package com.example.scoreviewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.SeekBar
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import java.io.File
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var seekBar: SeekBar
    private var document: Document? = null
    private var previewPopup: PopupWindow? = null
    private val PICK_PDF_FILE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 상단 툴바 연결
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // PDF 뷰어 및 페이지 넘김 연결
        viewPager = findViewById(R.id.viewPager)
        seekBar = findViewById(R.id.pageSeekBar)

        // PDF 파일 선택
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { // ← 버튼 ID
                openFilePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("selected_pdf", ".pdf", cacheDir)
            tempFile.outputStream().use { output -> inputStream?.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openPdf(pdfFile: File?) {
        if (pdfFile == null) {
            Log.e("PDFViewer", "Exception : PDF 파일 선택 실패")
            return
        }

        // 기존 문서 닫기
        document?.destroy()
        document = Document.openDocument(pdfFile.absolutePath)
        val pageCount = document!!.countPages()

        // PDF 어댑터 연결
        viewPager.adapter = PDFPagerAdapter(document!!, pageCount)

        // SeekBar 설정
        seekBar.max = pageCount - 1
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar?) {
                previewPopup = PopupWindow(this@MainActivity)
                val previewImage = ImageView(this@MainActivity).apply {
                    adjustViewBounds = true
                }
                previewPopup?.contentView = previewImage
                previewPopup?.width = 200
                previewPopup?.height = 300

                sb?.let {
                    previewPopup?.showAsDropDown(it, 0, -it.height - (previewPopup?.height ?: 300))
                }
            }

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val bitmap = getPageThumbnail(progress)
                    (previewPopup?.contentView as? ImageView)?.setImageBitmap(bitmap)
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                previewPopup?.dismiss()
                previewPopup = null
                viewPager.setCurrentItem(sb?.progress ?: 0, true)
            }
        })

        // ViewPager 페이지 변경 → SeekBar 위치 동기화
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                seekBar.progress = position
            }
        })
    }

    private fun getPageThumbnail(pageIndex: Int): Bitmap? {
        return try {
            val page: Page = document?.loadPage(pageIndex) ?: return null
            val matrix = Matrix.Scale(0.2f)
            val pixmap: Pixmap = page.toPixmap(matrix, ColorSpace.DeviceRGB, true, true)
            val width = pixmap.width
            val height = pixmap.height
            val pixels = pixmap.pixels
            val bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            pixmap.destroy()
            page.destroy()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}