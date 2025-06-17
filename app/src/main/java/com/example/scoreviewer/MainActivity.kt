package com.example.scoreviewer

import Section
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.larswerkman.holocolorpicker.ColorPicker
import java.io.File
import androidx.core.view.isVisible
import androidx.core.content.edit
import android.Manifest

class MainActivity : AppCompatActivity(), BookmarkDialogFragment.HostCallback {

    private val pdfManager = PdfManager()
    private var currentPdfUri: Uri? = null
    private lateinit var originalPdfBaseName: String
    private lateinit var viewPager: ViewPager2
    private lateinit var bookmarkSeekBar: BookmarkSeekBar
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
    private lateinit var btnPage: ImageButton

    private lateinit var pageMenuPanel: View
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
    private var currentMusicXmlFile: File? = null

    private lateinit var bookmarkPrefs: SharedPreferences
    private val bookmarks = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 앱 최초 실행 시 모든 설정 초기화
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        if (isFirstRun) {
            getSharedPreferences("PlaybackPrefs", MODE_PRIVATE).edit().clear().apply()
            prefs.edit().apply {
                remove("sync_offset_ms")
                remove("start_delay_sec")
                putBoolean("is_first_run", false)
                apply()
            }
            viewPager = findViewById(R.id.viewPager)
            viewPager.setCurrentItem(0, false)
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // View 초기화
        viewPager = findViewById(R.id.viewPager)
        bookmarkSeekBar = findViewById(R.id.pageSeekBar)
        thumbnailContainer = findViewById(R.id.thumbnail_container)

        // 최초 실행 시 ViewPager 페이지 초기화
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val firstRun = sharedPrefs.getBoolean("is_first_run", true)
        if (firstRun) {
            viewPager.setCurrentItem(0, false)
        }

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
        btnPage = findViewById(R.id.btnPage)

        pageMenuPanel = findViewById(R.id.pageMenuPanel)
        canvasPreviewSize = findViewById(R.id.canvasPreviewSize)
        canvasPreviewColor = findViewById(R.id.canvasPreviewColor)
        btnDecreaseSize = findViewById(R.id.btnDecreaseSize)
        btnIncreaseSize = findViewById(R.id.btnIncreaseSize)
        colorPicker = findViewById(R.id.colorPicker)
        canvasPreview = findViewById(R.id.CanvasPreview)
        pageMenuPanel.findViewById<ImageButton>(R.id.btn_bookmark_toggle)
            .setOnClickListener {
                toggleBookmark(viewPager.currentItem)
                pageMenuPanel.visibility = View.GONE
            }
        pageMenuPanel.findViewById<ImageButton>(R.id.btn_bookmark_list)
            .setOnClickListener {
                openBookmarkList()
                pageMenuPanel.visibility = View.GONE
            }
        pageMenuPanel.findViewById<ImageButton>(R.id.btn_add_page)
            .setOnClickListener {
                promptAddPage(viewPager.currentItem)
                pageMenuPanel.visibility = View.GONE
            }
        pageMenuPanel.findViewById<ImageButton>(R.id.btn_delete_page)
            .setOnClickListener {
                promptDeletePage(viewPager.currentItem)
                pageMenuPanel.visibility = View.GONE
            }

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

        btnPlay = findViewById(R.id.btnPlay)

        // 최초 실행 여부를 onCreate에서 미리 읽어둠

        btnPlay.setOnClickListener {
            currentPdfFile?.let {
                val intent = Intent(this, PlayActivity::class.java).apply {
                    putExtra("pdfPath", it.absolutePath)
                    putExtra("midiPath", currentMidiFile?.absolutePath)
                    putExtra("musicXmlPath", currentMusicXmlFile?.absolutePath)
                    putExtra("resetPrefs", isFirstRun)
                    putExtra("autoMatchFailed", (currentMidiFile == null && currentMusicXmlFile == null))
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

        btnPage.setOnClickListener {
            pageMenuPanel.visibility =
                if (pageMenuPanel.isVisible)
                    View.GONE
                else
                    View.VISIBLE
        }
        // PageBar 초기화할 때에도 커스텀 SeekBar를 넘겨줍니다
        pageBar = PageBar(pdfManager, pdfManager.pageCount()).also {
            it.initializeSeekBar(bookmarkSeekBar)
            it.onPageSelected = { page ->
                viewPager.setCurrentItem(page, true)
            }
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
        btnSave.setOnClickListener {
            if (Build.VERSION.SDK_INT < 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
                return@setOnClickListener
            }

            showSaveDialog()
        }
        annotationCanvas.onTextTapListener = { modelX, modelY ->
            // 1) 혹시 전에 올라와 있던 EditText가 있으면 제거
            thumbnailContainer.findViewWithTag<EditText>("inlineEdit")?.let {
                thumbnailContainer.removeView(it)
            }

            // 2) "모델 좌표(modelX, modelY)" → "캔버스 내부 픽셀 좌표"로 변환
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

            // 7) LayoutParams에 "절대 위치 → thumbnailContainer 내부 좌표"를 베이스라인 기준으로 세팅
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

        // Long-press 메뉴 버튼
        pageMenuPanel.findViewById<ImageButton>(R.id.btn_bookmark_toggle)
            .setOnClickListener {
                val page = viewPager.currentItem
                toggleBookmark(page)
                pageMenuPanel.visibility = View.GONE
            }

        btnPage.setOnClickListener {
            // 메뉴가 열리기 직전에 아이콘 상태 동기화
            updateBookmarkIcon(viewPager.currentItem)
            pageMenuPanel.visibility = if (pageMenuPanel.isVisible) View.GONE else View.VISIBLE
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
        if (canvasToolsPanel.isVisible && !isCanvasActive) {
                isCanvasActive = true
                annotationCanvas.setTool(toolController.getCurrentTool())
                // 시각 피드백: 반투명 아이콘
                btnCanvas.alpha = 0.5f
            }
    }

    /** PDF 파일 선택 위한 Intent */
    // 1) 액티비티 결과 런처 등록
    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.also { uri ->
                handlePickedPdf(uri)
            }
        }
    }

    // 2) 파일 선택 메서드 수정
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pdfPickerLauncher.launch(intent)  // ← startActivityForResult 대신 launch()
    }

    // 3) 기존 onActivityResult 제거 후 대체
    private fun handlePickedPdf(uri: Uri) {
        currentPdfUri = uri
        copyUriToTempFile(uri)?.let { file ->
            openPdf(file)
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
        // 새 PDF를 열면 이전 세션 복원 정보를 모두 초기화
        LastPlayCache.clear()
        getSharedPreferences("PlaybackPrefs", MODE_PRIVATE).edit().clear().apply()

        viewPager.offscreenPageLimit = 2
        (viewPager.getChildAt(0) as RecyclerView).setItemViewCacheSize(2)
        currentPdfFile = pdfFile
        pdfManager.open(pdfFile.absolutePath)
        val count = pdfManager.pageCount()

        viewPager.adapter = PDFPagerAdapter(pdfManager, count, annotationCanvas, viewPager)
        annotationCanvas.clearAll()
        66
        val prefs = getSharedPreferences("PlaybackPrefs", MODE_PRIVATE)
        prefs.edit { putString("last_pdf", pdfFile.absolutePath) }

        pageBar = PageBar(pdfManager, count).also {
            it.initializeSeekBar(bookmarkSeekBar)
            it.onPageSelected = { page -> viewPager.setCurrentItem(page, true) }
            it.onThumbnailRequested = { bm, x, y -> handleThumbnailRequest(bm, x, y) }
            //seekbar false : default
            isSeekBarActive = false
            it.setSeekBarActive(false)
            btnToggleSeekBar.setImageResource(R.drawable.baseline_toggle_off_24)
        }

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback(){
            override fun onPageSelected(position: Int) {
                bookmarkSeekBar.progress = position
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

        // 항상 열 때마다 원본 베이스네임 갱신
        val display = currentPdfUri?.let { queryFileName(it) }
        originalPdfBaseName = display
            ?.substringBeforeLast('.')
            ?: pdfFile.nameWithoutExtension

        // 자동매칭: 원본 파일명 기반으로 외부 저장소에서 MIDI/MusicXML 검색
        currentMidiFile = null
        currentMusicXmlFile = null
        val midiName = originalPdfBaseName + ".mid"
        val musicXmlName = originalPdfBaseName + ".xml"
        // 1. PDF와 같은 폴더(가능하다면)에서 먼저 검색
        val midiFromCache = File(pdfFile.parentFile, midiName)
        val xmlFromCache = File(pdfFile.parentFile, musicXmlName)
        if (midiFromCache.exists()) currentMidiFile = midiFromCache
        if (xmlFromCache.exists()) currentMusicXmlFile = xmlFromCache
        // 2. 그래도 없으면 Downloads, Documents 등에서 검색
        if (currentMidiFile == null) {
            currentMidiFile = findFileInCommonDirs(midiName)
        }
        if (currentMusicXmlFile == null) {
            currentMusicXmlFile = findFileInCommonDirs(musicXmlName)
        }

        // (1) PDF 파일이 열릴 때마다 prefs 초기화
        bookmarkPrefs = getSharedPreferences(
            "Bookmarks_${originalPdfBaseName}", MODE_PRIVATE
        )
        // (2) 저장된 문자열 세트(String)에 담긴 숫자들로 변환
        bookmarks.clear()
        bookmarkPrefs.getStringSet("bookmarks", emptySet())!!
            .mapNotNull { it.toIntOrNull() }
            .forEach { bookmarks.add(it) }



        // (3) SeekBar 위에 표시하기 위해 PageBar에 북마크 전달 (다음 단계 구현용)
        pageBar?.setBookmarks(bookmarks)
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
            bookmarkSeekBar.progress = savedPage
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val labelTitle    = dialogView.findViewById<TextView>(R.id.labelTitle)
        val editTitle     = dialogView.findViewById<EditText>(R.id.editTitle)
        val textPathView  = dialogView.findViewById<TextView>(R.id.textPath)
        val radioGroup    = dialogView.findViewById<RadioGroup>(R.id.radioGroupSaveType)
        val radioFlatten  = dialogView.findViewById<RadioButton>(R.id.radio_flattenPdf)

        // 저장 경로...
        val saveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        textPathView.text = "저장 경로: ${saveDir.absolutePath}"

        // 추천 이름 세팅
        val suggestion = SaveAnnotatedPDF.generateSaveFileName(
            this,
            File("$originalPdfBaseName.pdf")
        )
        editTitle.setText(suggestion)
        editTitle.setSelection(suggestion.length)

        // 초기 선택: '파일 저장'
        radioFlatten.isChecked = true
        labelTitle.visibility = View.GONE
        editTitle.visibility  = View.GONE

        // 그룹 리스너
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_flattenPdf) {
                labelTitle.visibility = View.GONE
                editTitle.visibility  = View.GONE
            } else {
                labelTitle.visibility = View.VISIBLE
                editTitle.visibility  = View.VISIBLE
            }
        }

        AlertDialog.Builder(this)
            .setTitle("저장 옵션")
            .setView(dialogView)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val overwrite = radioFlatten.isChecked
                val baseName = if (overwrite) originalPdfBaseName
                               else editTitle.text.toString().ifBlank { suggestion }
                val saved = SaveAnnotatedPDF.save(
                        pdfManager,
                        annotationCanvas,
                        outputName = baseName,
                        overwrite  = overwrite
                )
                Toast.makeText(this, "${saved.name}에 저장했습니다.", Toast.LENGTH_SHORT).show()
                openPdf(saved)
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
        if (pageMenuPanel.isVisible && ev.action == MotionEvent.ACTION_DOWN) {
            val menuRect = Rect()
            pageMenuPanel.getGlobalVisibleRect(menuRect)
            // 메뉴 토글 버튼도 터치 허용 영역으로 포함시키려면 아래처럼 버튼 영역도 같이 가져옵니다.
            val btnRect = Rect()
            btnPage.getGlobalVisibleRect(btnRect)

            val x = ev.rawX.toInt()
            val y = ev.rawY.toInt()
            // 패널 외부, 버튼 영역 외부를 터치하면 닫기
            if (!menuRect.contains(x, y) && !btnRect.contains(x, y)) {
                pageMenuPanel.visibility = View.GONE
                return false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun toggleBookmark(page: Int) {
        if (bookmarks.contains(page)) {
            bookmarks.remove(page)
        } else {
            bookmarks.add(page)
        }
        // SharedPreferences 에 저장
        bookmarkPrefs.edit {
            putStringSet("bookmarks", bookmarks.map { it.toString() }.toSet())
        }
        // 메뉴 아이콘·PageBar 에 반영
        updateBookmarkIcon(page)
        pageBar?.setBookmarks(bookmarks)
    }

    private fun openBookmarkList() {
        showBookmarkDialog()
    }

    private fun promptAddPage(page: Int) {
        // 페이지 추가 다이얼로그 로직
    }

    private fun promptDeletePage(page: Int) {
        // 페이지 삭제 다이얼로그 로직
    }

    private fun updateBookmarkIcon(page: Int) {
        val btn = pageMenuPanel.findViewById<ImageButton>(R.id.btn_bookmark_toggle)
        val iconRes = if (bookmarks.contains(page))
            R.drawable.ic_star_on_24
        else
            R.drawable.ic_star_off_24
        btn.setImageResource(iconRes)
    }

    private fun showBookmarkDialog() {
        // PDF 이름과 페이지 개수를 기반으로 단일 구간 생성
        val baseName = originalPdfBaseName
        val totalPages = pdfManager.pageCount()
        val sections = arrayListOf(
            Section(
                name      = baseName,
                startPage = 0,
                endPage   = totalPages - 1
            )
        )

        // 북마크 이동 다이얼로그에 전달
        val frag = BookmarkDialogFragment().apply {
            arguments = Bundle().apply {
                putString("pdfBaseName", baseName)
                putParcelableArrayList("sections", sections)
            }
        }
        frag.show(supportFragmentManager, "bookmark_nav")
    }
    override fun onNavigateToPage(page: Int) {
        viewPager.currentItem = page
    }
    override fun onBookmarksChanged(newBookmarks: Set<Int>) {
        // 1) 메모리에 담긴 bookmarks 갱신
        bookmarks.clear()
        bookmarks.addAll(newBookmarks)

        // 2) SharedPreferences에는 이미 다이얼로그에서 반영됐으니, UI만 갱신
        updateBookmarkIcon(viewPager.currentItem)
        pageBar?.setBookmarks(bookmarks)
    }
    private fun findFileInCommonDirs(fileName: String): File? {
        val dirs = listOfNotNull(
            getExternalFilesDir(null),
            getExternalFilesDir(""),
            getExternalFilesDir("Documents"),
            getExternalFilesDir("Download"),
            getExternalFilesDir("Music"),
            getExternalFilesDir("Movies"),
            getExternalFilesDir("Pictures"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        )
        for (dir in dirs) {
            val file = File(dir, fileName)
            if (file.exists()) return file
        }
        return null
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 사용자가 방금 허용했으면 바로 저장 실행
                showSaveDialog()
            } else {
                Toast.makeText(this, "저장을 위해 저장 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
