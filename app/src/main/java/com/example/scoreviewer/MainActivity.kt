package com.example.scoreviewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.inputmethod.InputMethodManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.larswerkman.holocolorpicker.ColorPicker
import java.io.File
import androidx.core.view.isVisible
import androidx.core.content.edit

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

    private lateinit var btnCanvas: CanvasToggleButton
    private lateinit var canvasToolsPanel: View
    private lateinit var canvasPreviewSize: TextView
    private lateinit var canvasPreviewColor: View
    private lateinit var btnDecreaseSize: ImageButton
    private lateinit var btnIncreaseSize: ImageButton
    private lateinit var gestureDetector: GestureDetector
    private lateinit var toolController: CanvasToolController
    private lateinit var colorPicker: ColorPicker
    private lateinit var canvasPreview: CanvasPreview
    private lateinit var pageChangeCallback: ViewPager2.OnPageChangeCallback

    private var isCanvasActive = false
    private var isSeekBarActive = true
    private var currentPdfFile: File? = null
    private var currentMidiFile: File? = null

    private val pickPDFFile = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 툴바 설정
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // View 초기화
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

        canvasPreviewSize = findViewById(R.id.canvasPreviewSize)
        canvasPreviewColor = findViewById(R.id.canvasPreviewColor)
        btnDecreaseSize = findViewById(R.id.btnDecreaseSize)
        btnIncreaseSize = findViewById(R.id.btnIncreaseSize)
        colorPicker = findViewById(R.id.colorPicker)
        canvasPreview = findViewById(R.id.CanvasPreview)

        // SeekBar 토글 버튼
        btnToggleSeekBar.setOnClickListener {
            isSeekBarActive = !isSeekBarActive
            pageBar?.setSeekBarActive(isSeekBarActive)
            val icon = if (isSeekBarActive)
                R.drawable.baseline_toggle_on_24
            else
                R.drawable.baseline_toggle_off_24
            btnToggleSeekBar.setImageResource(icon)
        }

        // MIDI 재생 버튼
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

        // GestureDetector로 btnCanvas에 단일/이중 탭 구분 로직 설정
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 단일 탭 → 캔버스 온/오프 토글
                handleSingleTapOnCanvasButton()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 이중 탭 → 툴 설정 패널 토글
                handleDoubleTapOnCanvasButton()
                return true
            }
        })
        btnCanvas.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            // GestureDetector가 이벤트를 처리하도록 한 뒤, ACTION_UP 시 performClick() 호출
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }

        toolController = CanvasToolController(
            annotationCanvas = annotationCanvas,
            btnCanvas = btnCanvas,
            panelContainer = canvasToolsPanel,
            btnPen = btnPen,
            btnHighlighter = btnHighlighter,
            btnText = btnText,
            btnEraser = btnEraser,

            // 크기/색상 조절용 뷰
            btnIncreaseSize = btnIncreaseSize,
            btnDecreaseSize = btnDecreaseSize,
            canvasPreviewColor = canvasPreviewColor,
            canvasPreviewSize = canvasPreviewSize,
            colorPicker         = colorPicker
        )
        toolController.bindPreview(canvasPreview)

        btnUndo.setOnClickListener { handleUndoOrRedo(isUndo = true) }
        btnRedo.setOnClickListener { handleUndoOrRedo(isUndo = false) }
        btnSave.setOnClickListener { showSaveDialog() }

        annotationCanvas.onTextTapListener = { modelX, modelY ->
            // 1) 혹시 전에 올라와 있던 EditText가 있으면 제거
            thumbnailContainer.findViewWithTag<EditText>("inlineEdit")?.let {
                thumbnailContainer.removeView(it)
            }

            // 2) “모델 좌표(modelX, modelY)” → “캔버스 내부 픽셀 좌표”로 변환
            //    (mapModelToScreen 은 AnnotationCanvasView에 미리 구현되어 있어야 합니다)
            val mappedPt = annotationCanvas.mapModelToScreen(modelX, modelY)
            val mappedX = mappedPt[0]            // 캔버스 내부 좌표의 X
            val mappedYBaseline = mappedPt[1]     // 캔버스 내부 좌표의 Y (베이스라인)

            // 3) 캔버스 뷰의 화면 내 절대 위치 구하기
            val canvasLoc = IntArray(2)
            annotationCanvas.getLocationOnScreen(canvasLoc)
            val absX = canvasLoc[0] + mappedX
            val absYBaseline = canvasLoc[1] + mappedYBaseline

            // 4) thumbnailContainer(부모 FrameLayout)의 절대 위치 구하기
            val containerLoc = IntArray(2)
            thumbnailContainer.getLocationOnScreen(containerLoc)
            //    → 이제 "절대 좌표"를 "thumbnailContainer 내부 좌표"로 변환
            val relX = (absX - containerLoc[0]).toInt()
            val relYBaseline = (absYBaseline - containerLoc[1]).toInt()

            // 5) 새로운 EditText 생성 (크기 관련 속성은 전혀 건드리지 않음)
            val edit = EditText(this).apply {
                tag = "inlineEdit"
                setBackgroundResource(android.R.drawable.edit_text)
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_DONE

                // (a) 색상만 패널에서 가져와서 적용
                setTextColor(toolController.getTextColor())

                // (b) 텍스트 크기는 그냥 기본값(14sp~16sp) 그대로 두기
                //     → 이 한 줄도 없앨 수 있습니다. (`textSize = ...` 자체를 쓰지 않음)

                setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        // 입력이 끝났을 때, 모델 좌표로 addText 호출
                        annotationCanvas.addText(v.text.toString(), modelX, modelY)
                        thumbnailContainer.removeView(v)
                        true
                    } else false
                }
            }

            // 6) EditText 내부 paint의 fontMetrics로부터 "베이스라인까지 거리" 계산
            //    → 얘가 없으면, EditText를 터치한 바로 그 지점에 올리지 못합니다.
            val fm = edit.paint.fontMetrics
            val baselineOffset = -fm.ascent
            //    (fm.ascent가 음수이므로, -ascent 하면 양수 픽셀 값이 나옵니다)

            // 7) LayoutParams에 “절대 위치 → thumbnailContainer 내부 좌표”를 베이스라인 기준으로 세팅
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = relX
                topMargin  = (relYBaseline - baselineOffset).toInt()
            }
            thumbnailContainer.addView(edit, params)
            edit.requestFocus()

            // 8) 키보드 올리기
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
        openFilePicker()
    }

    /**
     * 단일 탭: 캔버스(on/off) 토글
     * - isCanvasActive 플래그로 상태 관리
     * - OFF 상태일 때: AnnotationCanvasView.setTool(null) 호출 → 그리기 비활성화
     * - ON 상태일 때: AnnotationCanvasView.setTool(currentTool) 호출 → 그리기 활성화
     */
    private fun handleSingleTapOnCanvasButton() {
        if (!isCanvasActive) {
            // 캔버스가 꺼져 있으면, 현재 선택된 도구로 켜기
            isCanvasActive = true
            annotationCanvas.setTool(toolController.getCurrentTool())
            btnCanvas.alpha = 0.5f
        } else {
            // 캔버스가 켜져 있으면, 끄기 (setTool(null)로 그리기 비활성화)
            isCanvasActive = false
            annotationCanvas.setTool(null)
            btnCanvas.alpha = 1f
        }
    }

    /** 이중 탭: 툴 설정 패널 토글 */
    private fun handleDoubleTapOnCanvasButton() {
        toolController.togglePanel()
    }

    /** PDF 파일 선택 위한 Intent */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, pickPDFFile)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickPDFFile && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                currentPdfUri = uri
                copyUriToTempFile(uri)?.let { file ->
                    openPdf(file)
                }
            }
        }
    }

    /** Uri를 임시 파일로 복사 */
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

    /** PDF 열어서 ViewPager에 연결 */
    private fun openPdf(pdfFile: File) {
        viewPager.offscreenPageLimit = 1
        currentPdfFile = pdfFile
        pdfManager.open(pdfFile.absolutePath)
        val count = pdfManager.pageCount()

        viewPager.adapter = PDFPagerAdapter(pdfManager, count, annotationCanvas, viewPager)
        annotationCanvas.clearAll()
        66
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

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback(){
            override fun onPageSelected(position: Int) {
                seekBar.progress = position
                annotationCanvas.setPage(position)
                // 현재 페이지의 transformation matrix도 넘겨 줌
                val rv = viewPager.getChildAt(0) as? RecyclerView
                val holder = rv
                    ?.findViewHolderForAdapterPosition(position)
                        as? PDFPagerAdapter.PageViewHolder
                holder?.let {
                    annotationCanvas.setTransformationMatrix(it.imageView.imageMatrix)
                }
            }
        }

        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        annotationCanvas.setPage(viewPager.currentItem)

        if (!::originalPdfBaseName.isInitialized) {
            val display = currentPdfUri?.let { queryFileName(it) }
            originalPdfBaseName = display
                ?.substringBeforeLast('.')
                ?: pdfFile.nameWithoutExtension
        }

        currentMidiFile = null
        val midiFile = File(pdfFile.parentFile, pdfFile.nameWithoutExtension + ".mid")
        if (midiFile.exists()) {
            currentMidiFile = midiFile
        } else {
            prefs.edit { remove("last_midi") }
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
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        pdfManager.close()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                openFilePicker()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    /** 저장 다이얼로그 표시 */
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
        val count = filesDir.listFiles { f ->
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
                val savedFile = SaveAnnotatedPDF.save(
                    pdfManager,
                    annotationCanvas,
                    title
                )
                Toast.makeText(this, "저장: $title ($method)", Toast.LENGTH_SHORT).show()
                openPdf(savedFile)

            }
            .show()
    }

    /** Uri에서 파일 이름 추출 */
    private fun queryFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return null
    }

    /** undo/redo 처리 */
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

    /** 툴 설정 패널이 열려 있는 상태에서, 패널 외부를 터치하면 닫기 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (canvasToolsPanel.isVisible && ev.action == MotionEvent.ACTION_DOWN) {
            val rect = Rect()
            canvasToolsPanel.getGlobalVisibleRect(rect)
            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()
            if (!rect.contains(x, y)) {
                canvasToolsPanel.visibility = View.GONE
                return false
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
