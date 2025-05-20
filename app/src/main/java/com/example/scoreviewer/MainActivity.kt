// MainActivity.kt
package com.example.scoreviewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import java.io.File

class MainActivity : AppCompatActivity() {

    private val pdfManager = PdfManager()

    private lateinit var viewPager: ViewPager2
    private lateinit var seekBar: SeekBar
    private lateinit var thumbnailContainer: FrameLayout
    private var pageBar: PageBar? = null
    private var fragThumbnail: Frag_Thumbnail? = null

    private lateinit var annotationCanvas: AnnotationCanvasView
    private lateinit var btnPen: ImageButton
    private lateinit var btnHighlighter: ImageButton
    private lateinit var btnText: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnToggleSeekBar: ImageButton
    private lateinit var btnPlay: ImageButton

    private var currentTool: Tool? = null
    private var isSeekBarActive = true
    private var currentPdfFile: File? = null
    private var currentMidiFile: File? = null

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

        annotationCanvas = findViewById(R.id.annotationCanvas)
        btnToggleSeekBar = findViewById(R.id.btnToggleSeekBar)
        btnPen = findViewById(R.id.btnPen)
        btnHighlighter = findViewById(R.id.btnHighlighter)
        btnText = findViewById(R.id.btnText)
        btnEraser = findViewById(R.id.btnEraser)
        btnUndo = findViewById(R.id.btnUndo)

        btnToggleSeekBar.setOnClickListener {
            isSeekBarActive = !isSeekBarActive
            pageBar?.setSeekBarActive(isSeekBarActive)
            val icon = if (isSeekBarActive)
                R.drawable.baseline_toggle_on_24
            else
                R.drawable.baseline_toggle_off_24
            btnToggleSeekBar.setImageResource(icon)
        }

        btnPlay = findViewById(R.id.btnPlay)
        btnPlay.setOnClickListener {
            currentPdfFile?.let {
                val intent = Intent(this, PlayActivity::class.java).apply {
                    putExtra("pdfPath", it.absolutePath)
                    putExtra("currentPage", viewPager.currentItem)
                    currentMidiFile?.let { midi ->
                        putExtra("midiPath", midi.absolutePath)
                    }
                }
                startActivity(intent)
            }
        }

        btnPen.setOnClickListener        { toggleTool(Tool.PEN, btnPen) }
        btnHighlighter.setOnClickListener{ toggleTool(Tool.HIGHLIGHTER, btnHighlighter) }
        btnText.setOnClickListener       { toggleTool(Tool.TEXT, btnText) }
        btnEraser.setOnClickListener     { toggleTool(Tool.ERASER, btnEraser) }
        btnUndo.setOnClickListener       { annotationCanvas.undoLast() }

        annotationCanvas.onTextTapListener = { x, y ->
            thumbnailContainer.findViewWithTag<EditText>("inlineEdit")?.let {
                thumbnailContainer.removeView(it)
            }
            val edit = EditText(this).apply {
                tag = "inlineEdit"
                setBackgroundResource(android.R.drawable.edit_text)
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_DONE
                setTextColor(Color.BLACK)
                setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        annotationCanvas.addText(v.text.toString(), x, y)
                        thumbnailContainer.removeView(v)
                        true
                    } else false
                }
            }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = x.toInt()
                topMargin  = y.toInt()
            }
            thumbnailContainer.addView(edit, params)
            edit.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }

        openFilePicker()
    }

    private fun toggleTool(tool: Tool, button: ImageButton) {
        if (currentTool == tool) {
            currentTool = null
            annotationCanvas.setTool(null)
            clearAllButtonHighlights()
            button.alpha = 1f
        } else {
            clearAllButtonHighlights()
            currentTool = tool
            annotationCanvas.setTool(tool)
            button.alpha = 0.5f
        }
    }

    private fun clearAllButtonHighlights() {
        btnPen.alpha = 1f
        btnHighlighter.alpha = 1f
        btnText.alpha = 1f
        btnEraser.alpha = 1f
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

        viewPager.offscreenPageLimit = 1
        currentPdfFile = pdfFile
        pdfManager.open(pdfFile.absolutePath)
        val count = pdfManager.pageCount()

        viewPager.adapter = PDFPagerAdapter(pdfManager, count)

        val prefs = getSharedPreferences("PlaybackPrefs", MODE_PRIVATE)
        prefs.edit().putString("last_pdf", pdfFile.absolutePath).apply()

        pageBar = PageBar(pdfManager, count).also {
            it.initializeSeekBar(seekBar)
            it.onPageSelected = { page -> viewPager.setCurrentItem(page, true) }
            it.onThumbnailRequested = { bm, x, y -> handleThumbnailRequest(bm, x, y) }
            //seekbar false : default
            isSeekBarActive = false
            it.setSeekBarActive(false)
            btnToggleSeekBar.setImageResource(R.drawable.baseline_toggle_off_24)
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                seekBar.progress = position
            }
        })
        //동일 이름 MIDI 파일 존재 시 자동 연결
        currentMidiFile = null //초기화
        val midiFile = File(pdfFile.parentFile, pdfFile.nameWithoutExtension + ".mid")
        if (midiFile.exists()) {
            currentMidiFile = midiFile
        }else {
            prefs.edit().remove("last_midi").apply()
        }


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
    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("PlaybackPrefs", MODE_PRIVATE)
        val savedPage = prefs.getInt("last_page", -1)
        val savedPdfPath = prefs.getString("last_pdf", null)

        if (savedPage != -1 && savedPdfPath != null && currentPdfFile?.absolutePath == savedPdfPath) {
            viewPager.setCurrentItem(savedPage, false)
            seekBar.progress = savedPage
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        openFilePicker()
        return true
    }
    override fun onDestroy() {
        super.onDestroy()
        pdfManager.close()
    }
}

