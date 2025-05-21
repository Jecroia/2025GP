// MainActivity.kt
package com.example.scoreviewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import java.io.File

class MainActivity : AppCompatActivity() {

    private val pdfManager = PdfManager()
    private var currentPdfUri: Uri? = null
    private lateinit var originalPdfBaseName: String

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
    private lateinit var btnRedo: ImageButton
    private lateinit var btnToggleSeekBar: ImageButton
    private lateinit var btnSave: ImageButton

    private var currentTool: Tool? = null
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

        annotationCanvas = findViewById(R.id.annotationCanvas)
        btnToggleSeekBar = findViewById(R.id.btnToggleSeekBar)
        btnPen = findViewById(R.id.btnPen)
        btnHighlighter = findViewById(R.id.btnHighlighter)
        btnText = findViewById(R.id.btnText)
        btnEraser = findViewById(R.id.btnEraser)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnSave = findViewById(R.id.btnSave)

        btnToggleSeekBar.setOnClickListener {
            isSeekBarActive = !isSeekBarActive
            pageBar?.setSeekBarActive(isSeekBarActive)
            val icon = if (isSeekBarActive)
                R.drawable.baseline_toggle_on_24
            else
                R.drawable.baseline_toggle_off_24
            btnToggleSeekBar.setImageResource(icon)
        }

        btnPen.setOnClickListener        { toggleTool(Tool.PEN, btnPen) }
        btnHighlighter.setOnClickListener{ toggleTool(Tool.HIGHLIGHTER, btnHighlighter) }
        btnText.setOnClickListener       { toggleTool(Tool.TEXT, btnText) }
        btnEraser.setOnClickListener     { toggleTool(Tool.ERASER, btnEraser) }
        btnUndo.setOnClickListener { handleUndoOrRedo(isUndo = true) }
        btnRedo.setOnClickListener { handleUndoOrRedo(isUndo = false) }
        btnSave.setOnClickListener       { showSaveDialog() }

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
                currentPdfUri = uri
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

        viewPager.adapter = PDFPagerAdapter(pdfManager, count)

        pageBar = PageBar(pdfManager, count).also {
            it.initializeSeekBar(seekBar)
            it.onPageSelected = { page -> viewPager.setCurrentItem(page, true) }
            it.onThumbnailRequested = { bm, x, y -> handleThumbnailRequest(bm, x, y) }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                seekBar.progress = position
                annotationCanvas.setPage(position)
            }
        })
        annotationCanvas.setPage(viewPager.currentItem)

        if (!::originalPdfBaseName.isInitialized) {
            val display = currentPdfUri?.let { queryFileName(it) }
                        originalPdfBaseName = display
                            ?.substringBeforeLast('.')
                            ?: pdfFile.nameWithoutExtension
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

    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.editTitle)
        val textPath = dialogView.findViewById<TextView>(R.id.textPath)
        val radioFlatten = dialogView.findViewById<RadioButton>(R.id.radio_flattenPdf)
        val radioSeparate = dialogView.findViewById<RadioButton>(R.id.radio_separate)

        // ─────────── 기본 파일명 계산 ───────────
        val filesDir = getExternalFilesDir(null)!!
        // 1) 기본 베이스 이름
        val base = "${originalPdfBaseName}_sv"
        // 2) 같은 이름의 PDF가 몇 개 있는지 세기
        val existing = filesDir.listFiles { f ->
            f.extension.equals("pdf", true) &&
                    f.nameWithoutExtension.startsWith(base)
        }?.size ?: 0
        // 3) suffix 붙이기 (_sv, _sv_2, _sv_3 …)
        val defaultName = if (existing <= 1) base else "${base}_$existing"
        // ───────────────────────────────────────

        // 미리 채워 두기
        editTitle.setText(defaultName)
        editTitle.setSelection(defaultName.length) // 커서를 맨 뒤로

        // 나머지 초기값
        dialogView.findViewById<RadioButton>(R.id.radio_flattenPdf).isChecked = true
        dialogView.findViewById<TextView>(R.id.textPath).text =
            "저장 경로: ${filesDir.absolutePath}"

        AlertDialog.Builder(this)
            .setTitle("필기 저장")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val title = editTitle.text.toString().ifBlank { defaultName }
                val method =
                    if (dialogView.findViewById<RadioButton>(R.id.radio_flattenPdf).isChecked)
                        "PDF로 저장" else "별도 파일로 저장"
                // TODO: saveAnnotatedPdf(title)
                Toast.makeText(this, "저장: $title ($method)", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    private fun queryFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return null
    }

    private fun handleUndoOrRedo(isUndo: Boolean) {
        val target = if (isUndo) annotationCanvas.peekUndo()
        else         annotationCanvas.peekRedo()

        if (target == null) return

        val (page, _, _) = target
        val currentPage = viewPager.currentItem

        if (page != currentPage) {
            AlertDialog.Builder(this)
                .setTitle("알림")
                .setMessage("다른 페이지(${page + 1}p)에서 작성된 필기입니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("실행") { _, _ ->
                    viewPager.setCurrentItem(page, false)
                    annotationCanvas.setPage(page)
                    if (isUndo) annotationCanvas.undoLast()
                    else        annotationCanvas.redoLast()
                }
                .show()
        } else {
            if (isUndo) annotationCanvas.undoLast()
            else        annotationCanvas.redoLast()
        }
    }

}

