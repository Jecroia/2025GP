package com.example.scoreviewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.commit
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.fitz.Document
import java.io.File
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var seekBar: SeekBar
    private lateinit var thumbnailContainer: FrameLayout
    private var document: Document? = null
    private val PICK_PDF_FILE = 1001
    private var pageBar: PageBar? = null
    private var fragThumbnail: Frag_Thumbnail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewPager = findViewById(R.id.viewPager)
        seekBar = findViewById(R.id.pageSeekBar)
        thumbnailContainer = findViewById(R.id.thumbnail_container)

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
            tempFile.outputStream().use { output -> inputStream?.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun openPdf(pdfFile: File?) {
        if (pdfFile == null) {
            Log.e("PDFViewer", "PDF 파일 선택 실패")
            return
        }

        document?.destroy()
        document = Document.openDocument(pdfFile.absolutePath)
        val pageCount = document!!.countPages()

        viewPager.adapter = PDFPagerAdapter(document!!, pageCount)

        // PageBar 생성 및 SeekBar 초기화
        pageBar = PageBar(document!!, pageCount)
        pageBar?.initializeSeekBar(seekBar)


        pageBar?.onPageSelected = { page ->
            viewPager.setCurrentItem(page, true)
        }

        // 썸네일 띄우는 콜백 (기존에 있던 코드)
        pageBar?.onThumbnailRequested = { thumbnail, xPos ->
            if (xPos < 0) {
                fragThumbnail?.let {
                    if (it.isAdded) {
                        supportFragmentManager.commit { remove(it) }
                        fragThumbnail = null
                    }
                }
            } else {
                val location = IntArray(2)
                seekBar.getLocationOnScreen(location)
                val yPos = location[1] - 10
                showOrUpdateThumbnail(thumbnail, xPos, yPos)
            }
        }

        // ViewPager → SeekBar 동기화
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                seekBar.progress = position
            }
        })
    }


    private fun showOrUpdateThumbnail(bitmap: Bitmap, xPos: Int, yPos: Int) {
        if (fragThumbnail == null) {
            fragThumbnail = Frag_Thumbnail()
            supportFragmentManager.commit {
                add(R.id.thumbnail_container, fragThumbnail!!, "thumbnail")
            }
            thumbnailContainer.post {
                fragThumbnail?.updateThumbnail(bitmap, xPos, yPos)
            }
        } else {
            fragThumbnail?.updateThumbnail(bitmap, xPos, yPos)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}
