package com.example.scoreviewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.ImageButton
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val pdfManager = PdfManager()

    private lateinit var viewPager: ViewPager2
    private lateinit var seekBar: SeekBar
    private lateinit var thumbnailContainer: FrameLayout
    private var pageBar: PageBar? = null
    private var fragThumbnail: Frag_Thumbnail? = null
    private lateinit var btnToggleSeekBar: ImageButton
    private var isSeekBarActive = true

    private val PICK_PDF_FILE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewPager = findViewById(R.id.viewPager)
        seekBar = findViewById(R.id.pageSeekBar)
        thumbnailContainer = findViewById(R.id.thumbnail_container)

        btnToggleSeekBar = findViewById(R.id.btnToggleSeekBar)

        btnToggleSeekBar.setOnClickListener {
            isSeekBarActive = !isSeekBarActive
            pageBar?.setSeekBarActive(isSeekBarActive)

            val icon = if (isSeekBarActive)
                R.drawable.baseline_toggle_on_24
            else
                R.drawable.baseline_toggle_off_24

            btnToggleSeekBar.setImageResource(icon)
        }

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
                copyUriToTempFile(uri)?.let { file ->
                    openPdf(file)
                }
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            File.createTempFile("selected_pdf", ".pdf", cacheDir).apply {
                outputStream().use { output -> input.copyTo(output) }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun openPdf(pdfFile: File) {
        pdfManager.open(pdfFile.absolutePath)
        val count = pdfManager.pageCount()

        // 뷰페이저 & 어댑터
        viewPager.adapter = PDFPagerAdapter(pdfManager, count)

        // seekbar + PageBar 초기화
        pageBar = PageBar(pdfManager, count).also {
            it.initializeSeekBar(seekBar)
            it.onPageSelected = { page -> viewPager.setCurrentItem(page, true) }
            it.onThumbnailRequested = { bm, x, y -> handleThumbnailRequest(bm, x, y) }
        }

        // ViewPager → SeekBar 동기화
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                seekBar.progress = position
            }
        })
    }

    private fun handleThumbnailRequest(bitmap: Bitmap, x: Int, y: Int) {
        if (x < 0 || y < 0) {
            fragThumbnail?.let {
                supportFragmentManager.beginTransaction()
                    .remove(it)
                    .commitAllowingStateLoss()
                fragThumbnail = null
            }
        } else {
            if (fragThumbnail == null) {
                fragThumbnail = Frag_Thumbnail.newInstance(x, y)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.thumbnail_container, fragThumbnail!!)
                    .commitAllowingStateLoss()
            }
            fragThumbnail?.updateThumbnail(bitmap, x, y)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfManager.close()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                openFilePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}

