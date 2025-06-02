// MainActivity.kt
package com.example.scoreviewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
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
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var btnPlay: ImageButton

    private lateinit var btnCanvas: ImageButton
    private lateinit var canvasToolsPanel: View
    private lateinit var btnDecreaseSize: Button
    private lateinit var btnIncreaseSize: Button
    private lateinit var tvSize: TextView
    private lateinit var viewSelectedColor: View
    private lateinit var tvPreview: TextView

    private var currentTool: Tool? = null
    private var isToolActive: Boolean = false
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
        btnCanvas = findViewById(R.id.btnCanvas)
        canvasToolsPanel = findViewById(R.id.canvasToolsPanel)

        btnPen = findViewById(R.id.btnPen)
        btnHighlighter = findViewById(R.id.btnHighlighter)
        btnText = findViewById(R.id.btnText)
        btnEraser = findViewById(R.id.btnEraser)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnSave = findViewById(R.id.btnSave)
        btnPlay = findViewById(R.id.btnPlay)

        btnDecreaseSize = findViewById(R.id.btnDecreaseSize)
        btnIncreaseSize = findViewById(R.id.btnIncreaseSize)
        tvSize = findViewById(R.id.tvSize)
        viewSelectedColor = findViewById(R.id.viewSelectedColor)
        tvPreview = findViewById(R.id.tvPreview)

        btnPen.isSelected         = false
        btnHighlighter.isSelected = false
        btnText.isSelected        = false
        btnEraser.isSelected      = false

        btnToggleSeekBar.setOnClickListener {
            isSeekBarActive = !isSeekBarActive
            pageBar?.setSeekBarActive(isSeekBarActive)
            val icon = if (isSeekBarActive)
                R.drawable.baseline_toggle_on_24
            else
                R.drawable.baseline_toggle_off_24
            btnToggleSeekBar.setImageResource(icon)
        }

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

        var lastClickTime = 0L
        btnCanvas.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 300) {
                // 더블탭: 툴 패널 보이기/숨기기
                canvasToolsPanel.visibility =
                    if (canvasToolsPanel.visibility == View.GONE) View.VISIBLE
                    else View.GONE
            } else {
                // 싱글탭: 도구 on/off
                handleSingleTapOnCanvasButton()
            }
            lastClickTime = now
        }

        // 패널 외부 터치 시 닫기
        val rootLayout = findViewById<View>(R.id.rootLayout)
        rootLayout.setOnTouchListener { _, event ->
            if (canvasToolsPanel.visibility == View.VISIBLE && event.action == MotionEvent.ACTION_DOWN) {
                val rect = Rect()
                canvasToolsPanel.getGlobalVisibleRect(rect)
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                if (!rect.contains(x, y)) {
                    canvasToolsPanel.visibility = View.GONE
                }
            }
            false
        }

        btnPen.setOnClickListener         { selectTool(Tool.PEN) }
        btnHighlighter.setOnClickListener { selectTool(Tool.HIGHLIGHTER) }
        btnText.setOnClickListener        { selectTool(Tool.TEXT) }
        btnEraser.setOnClickListener      { selectTool(Tool.ERASER) }

        btnDecreaseSize.setOnClickListener {
            val currentSize = tvSize.text.toString().toIntOrNull() ?: 48
            val newSize = (currentSize - 4).coerceAtLeast(4)
            tvSize.text = "$newSize"
            adjustPreviewSize(newSize)
        }
        btnIncreaseSize.setOnClickListener {
            val currentSize = tvSize.text.toString().toIntOrNull() ?: 48
            val newSize = (currentSize + 4).coerceAtMost(200)
            tvSize.text = "$newSize"
            adjustPreviewSize(newSize)
        }

        // 색상 선택 영역(간단히 토글 예시)
        viewSelectedColor.setOnClickListener {
            val currentColor = (viewSelectedColor.background as? ColorDrawable)?.color
                ?: Color.RED
            val newColor = if (currentColor == Color.RED) Color.BLUE else Color.RED
            viewSelectedColor.setBackgroundColor(newColor)
            tvPreview.setTextColor(newColor)
        }

        btnUndo.setOnClickListener { handleUndoOrRedo(isUndo = true) }
        btnRedo.setOnClickListener { handleUndoOrRedo(isUndo = false) }
        btnSave.setOnClickListener { showSaveDialog() }

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

    private fun handleSingleTapOnCanvasButton() {
        if (currentTool == null) {
                selectTool(Tool.PEN)
                return
            }
        if (!isToolActive) {
            annotationCanvas.setTool(currentTool)
            isToolActive = true
            btnCanvas.alpha = 0.5f
        } else {
            annotationCanvas.setTool(null)
            isToolActive = false
            btnCanvas.alpha = 1f
        }
    }

    private fun selectTool(tool: Tool) {
        // 이전에 선택된 버튼들 모두 isSelected = false
        btnPen.isSelected = false
        btnHighlighter.isSelected = false
        btnText.isSelected = false
        btnEraser.isSelected = false

        // 새로 선택된 버튼만 isSelected = true
        when (tool) {
            Tool.PEN         -> btnPen.isSelected = true
            Tool.HIGHLIGHTER -> btnHighlighter.isSelected = true
            Tool.TEXT        -> btnText.isSelected = true
            Tool.ERASER      -> btnEraser.isSelected = true
        }

        // Canvas 도구 설정
        annotationCanvas.setTool(tool)
        currentTool = tool
        isToolActive = true
        btnCanvas.alpha = 0.5f

        // 툴 패널 닫기
        canvasToolsPanel.visibility = View.GONE

        // 미리보기 모드 설정
        when (tool) {
            Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER -> setPreviewMode(PreviewMode.LINE)
            Tool.TEXT                               -> setPreviewMode(PreviewMode.TEXT)
        }
    }

    private enum class PreviewMode { LINE, TEXT, NONE }

    private fun setPreviewMode(mode: PreviewMode) {
        when (mode) {
            PreviewMode.LINE -> {
                tvPreview.visibility = View.VISIBLE
                tvPreview.text = ""
                val params = tvPreview.layoutParams
                val heightPx = (24 * resources.displayMetrics.density).toInt()
                params.height = heightPx
                tvPreview.layoutParams = params
                tvPreview.setBackgroundColor(
                    (viewSelectedColor.background as? ColorDrawable)?.color
                        ?: Color.RED
                )
            }
            PreviewMode.TEXT -> {
                tvPreview.visibility = View.VISIBLE
                tvPreview.text = "ABC abc 123"
                val params = tvPreview.layoutParams
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                tvPreview.layoutParams = params
                tvPreview.setBackgroundColor(Color.TRANSPARENT)
                tvPreview.setTextColor(
                    (viewSelectedColor.background as? ColorDrawable)?.color
                        ?: Color.RED
                )
                val sizeSp = tvSize.text.toString().toFloatOrNull() ?: 48f
                tvPreview.textSize = sizeSp
            }
            PreviewMode.NONE -> {
                tvPreview.visibility = View.GONE
            }
        }
    }

    // 크기 변화에 따라 미리보기 높이나 글자 크기 동기화
    private fun adjustPreviewSize(newSize: Int) {
        when (currentTool) {
            Tool.PEN, Tool.HIGHLIGHTER, Tool.ERASER -> {
                tvPreview.text = ""
                val params = tvPreview.layoutParams
                params.height = (newSize * resources.displayMetrics.density / 2).toInt().coerceAtLeast(8)
                tvPreview.layoutParams = params
            }
            Tool.TEXT -> {
                tvPreview.textSize = newSize.toFloat()
            }
            else -> {}
        }
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

        viewPager.offscreenPageLimit = 1
        currentPdfFile = pdfFile
        pdfManager.open(pdfFile.absolutePath)
        val count = pdfManager.pageCount()

        viewPager.adapter = PDFPagerAdapter(pdfManager, count, annotationCanvas, viewPager)
        annotationCanvas.clearAll()

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
                annotationCanvas.setPage(position)

                val rv = viewPager.getChildAt(0) as? RecyclerView
                val holder = rv
                    ?.findViewHolderForAdapterPosition(position)
                        as? PDFPagerAdapter.PageViewHolder
                holder?.let {
                    annotationCanvas.setTransformationMatrix(it.imageView.imageMatrix)
                }
            }
        })
        annotationCanvas.setPage(viewPager.currentItem)

        if (!::originalPdfBaseName.isInitialized) {
            val display = currentPdfUri?.let { queryFileName(it) }
            originalPdfBaseName = display
                ?.substringBeforeLast('.')
                ?: pdfFile.nameWithoutExtension
        }
        //동일 이름 MIDI 파일 존재 시 자동 연결
        currentMidiFile = null //초기화
        val midiFile = File(pdfFile.parentFile, pdfFile.nameWithoutExtension + ".mid")
        if (midiFile.exists()) {
            currentMidiFile = midiFile
        } else {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                openFilePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun showSaveDialog() {
        // 다이얼로그 뷰 inflate
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val editTitle = dialogView.findViewById<EditText>(R.id.editTitle)
        val textPathView = dialogView.findViewById<TextView>(R.id.textPath)
        val radioFlatten = dialogView.findViewById<RadioButton>(R.id.radio_flattenPdf)
        val radioSeparate = dialogView.findViewById<RadioButton>(R.id.radio_separate)

        // 기본 파일명 계산
        val filesDir = getExternalFilesDir(null)!!
        val baseName = "${originalPdfBaseName}_sv"
        val count    = filesDir.listFiles { f ->
            f.extension.equals("pdf", ignoreCase = true)
                    && f.nameWithoutExtension.startsWith(baseName)
        }?.size ?: 0
        val defaultName = if (count <= 1) baseName else "${baseName}_$count"

        // 뷰 초기화
        editTitle.apply {
            setText(defaultName)
            setSelection(text.length)
        }
        textPathView.text = "저장 경로: ${filesDir.absolutePath}"
        radioFlatten.isChecked = true

        // 다이얼로그 생성
        AlertDialog.Builder(this)
            .setTitle("필기 저장")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val title = editTitle.text.toString().ifBlank { defaultName }
                val method = if (radioFlatten.isChecked) "PDF로 저장" else "별도 파일로 저장"
                // TODO: 실제 저장 로직 호출 (e.g. saveAnnotatedPdf(title, method))
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