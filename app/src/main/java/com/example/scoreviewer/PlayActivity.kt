package com.example.scoreviewer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.abs

class PlayActivity : AppCompatActivity() {
    private val PICK_MIDI_FILE = 2001
    private val PICK_MUSICXML_FILE = 3001
    private val PERMISSION_REQUEST_CODE = 1001

    private lateinit var viewPager: ViewPager2
    private lateinit var pdfManager: PdfManager
    private lateinit var midiSeekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var annotationCanvas: AnnotationCanvasView
    private lateinit var midiPlaybackManager: MidiPlaybackManager
    private lateinit var syncPanelManager: SyncPanelManager

    private lateinit var syncOffsetInput: EditText
    private lateinit var startDelayInput: EditText

    private var pageCount = 0
    private var pdfPath: String? = null
    private var midiPath: String? = null
    private var musicXmlPath: String? = null
    private var pageChangeTimes: List<Int> = emptyList()
    private var currentLines: List<MusicXmlParser.Line> = emptyList()
    private var currentLineIndex = -1
    private var musicJsonData: JSONObject? = null
    private val PREF_NAME = "PlaybackPrefs"
    private lateinit var playbackState: PlaybackState
    private var restoredPage: Int = 0
    private var restoredMillis: Int = 0

    // PDF마다 유니크한 ID 생성 ("코드_pdf이름_크기_타임스탬프")
    private fun pdfId(pdfPath: String): String {
        val f = File(pdfPath)
        return "${f.name}_${f.length()}_${f.lastModified()}"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)
        Log.i("PlayActivity", "autoMatch result — midiPath: $midiPath, musicXmlPath: $musicXmlPath")

        // 권한 체크 및 요청
        checkAndRequestPermissions()

        pdfPath = intent.getStringExtra("pdfPath")
        midiPath = intent.getStringExtra("midiPath")
        musicXmlPath = intent.getStringExtra("musicXmlPath")
        val autoMatchFailed = intent.getBooleanExtra("autoMatchFailed", false)
        initializeViews()
        playbackState = PlaybackState(this)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val resetPrefs = intent.getBooleanExtra("resetPrefs", false)
        if (resetPrefs && pdfPath != null) {
            clearPdfSpecificSettings(prefs, pdfPath!!)
        } else if (pdfPath != null) {
            loadPdfSpecificSettings(prefs, pdfPath!!)
        }
        findViewById<Button>(R.id.btnApplySync).setOnClickListener {
            applyPdfSpecificSettings(prefs, pdfPath)
        }
        setupToolbar()
        setupPlaybackManager()
        setupSyncPanel()
        setupPlaybackState()
        loadSavedFiles()
        checkForMidiFile()
        if (pdfPath != null) loadPdfFile()
        if (midiPath != null) loadMidiFile()
        if (musicXmlPath != null) {
            val xmlFile = File(musicXmlPath!!)
            if (xmlFile.exists() && xmlFile.canRead()) {
                pageChangeTimes = MusicXmlParser.parsePageChangeTimes(xmlFile)
                currentLines = MusicXmlParser.parseLines(xmlFile)
                loadJsonForHighlight()
                annotationCanvas.post {
                    currentLineIndex = -1
                    updateCurrentLine(midiPlaybackManager.getCurrentTime())
                }
            } else {
                Log.w("PlayActivity", "MusicXML file not readable in onCreate; waiting for user selection")
            }
        }
        setupControlButtons()
        setupSeekBar()
        if (autoMatchFailed) {
            Toast.makeText(this, "자동으로 MIDI/MusicXML 파일을 찾지 못했습니다. 직접 선택해 주세요.", Toast.LENGTH_LONG).show()
            openMidiFilePicker()
            openMusicXmlFilePicker()
        }
        findViewById<ImageButton>(R.id.btnSelectMusicXml)?.setOnClickListener {
            openMusicXmlFilePicker()
        }

        // 페이지가 변경될 때 하이라이트 클리어
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                annotationCanvas.clearHighlight()
            }
        })
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.viewPager)
        midiSeekBar = findViewById(R.id.midiSeekBar)
        timeText = findViewById(R.id.txtCurrentTime)
        annotationCanvas = findViewById(R.id.annotationCanvas)
        pdfManager = PdfManager()
        syncOffsetInput = findViewById(R.id.editSyncOffset)
        startDelayInput = findViewById(R.id.editStartDelay)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.playToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupPlaybackManager() {
        midiPlaybackManager = MidiPlaybackManager(
            context = this,
            onTimeUpdate = { currentMillis, totalMillis ->
                updateTimeDisplay(currentMillis, totalMillis)
                updateCurrentLine(currentMillis)
                if (pageChangeTimes.isNotEmpty()) {
                    val nextPage = pageChangeTimes.indexOfLast { it <= currentMillis }
                    if (nextPage != -1 && nextPage != viewPager.currentItem) {
                        viewPager.setCurrentItem(nextPage, true)
                    }
                }
            },
            onPageTransition = { pageNumber ->
                if (pageChangeTimes.isEmpty()) {
                    viewPager.setCurrentItem(pageNumber, true)
                }
            },
            onError = { errorMessage ->
                runOnUiThread {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun setupSyncPanel() {
        syncPanelManager = SyncPanelManager(
            context = this,
            syncPanel = findViewById(R.id.syncPanel),
            syncOffsetInput = findViewById(R.id.editSyncOffset),
            startDelayInput = findViewById(R.id.editStartDelay),
            btnApplySync = findViewById(R.id.btnApplySync),
            onSyncSettingsChanged = { syncOffset, startDelay ->
                midiPlaybackManager.setSyncOffset(syncOffset)
            }
        )

        // 저장된 싱크 오프셋 값 로드
        val savedSyncOffset = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("sync_offset_ms", 0)
        midiPlaybackManager.setSyncOffset(savedSyncOffset)

        findViewById<ImageButton>(R.id.btnSetting).setOnClickListener {
            syncPanelManager.toggle()
        }
    }

    private fun setupPlaybackState() {
        playbackState = PlaybackState(this)
    }

    private fun loadSavedFiles() {
        // 1) PDF 경로 확보
        pdfPath = intent.getStringExtra("pdfPath") ?: return
        loadPdfFile()

        // 2) PDF별로 저장된 페이지·밀리초 불러오기
        val (savedPage, savedMillis) = loadPositionForPdf(this, pdfPath!!)
        restoredPage   = savedPage
        restoredMillis = savedMillis

        // 3) PDF별로 저장된 MIDI 경로 불러오기
        midiPath = intent.getStringExtra("midiPath")
            ?: loadMidiForPdf(this, pdfPath!!)
        if (midiPath != null) loadMidiFile()

        // 4) 복원 다이얼로그 조건
        if (midiPath != null && (restoredPage != 0 || restoredMillis != 0)) {
            showRestoreDialog()
        }
    }

    private fun loadPdfFile() {
        pdfManager.open(pdfPath!!)
        pageCount = pdfManager.pageCount()
        val adapter = PDFPagerAdapter(
            pdfManager, pageCount, annotationCanvas, viewPager
        )
        viewPager.adapter = adapter

        // AnnotationCanvas 가 현재 페이지 비트맵 크기에 접근할 수 있도록 provider 주입
        annotationCanvas.setBitmapProvider { idx -> adapter.getBitmap(idx) }
    }

    private fun loadMidiFile() {
        val midiFile = File(midiPath!!)
        try {
            midiPlaybackManager.initialize(midiFile, pageCount)
            midiSeekBar.max = midiPlaybackManager.getTotalTime()
            Log.d("PlayActivity", "MIDI Loaded: $midiPath")
        } catch (e: MidiPlaybackManager.MidiError) {
            // 에러 메시지는 이미 MidiPlaybackManager에서 처리됨
            midiPath = null
        } catch (e: Exception) {
            Toast.makeText(this, "예기치 않은 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            midiPath = null
        }
    }
    fun loadPositionForPdf(context: Context, pdfPath: String): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val id = pdfId(pdfPath)
        // 기본값으로는 글로벌 KEY_PAGE/KEY_MILLIS 사용
        val page  = prefs.getInt("${id}_page",  prefs.getInt("last_page",   0))
        val millis= prefs.getInt("${id}_millis",prefs.getInt("last_millis", 0))
        return page to millis
    }

    fun loadMidiForPdf(context: Context, pdfPath: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val id = pdfId(pdfPath)
        return prefs.getString("${id}_midiPath", null)
    }
    private fun setupControlButtons() {
        findViewById<ImageButton>(R.id.btnPlay).setOnClickListener {
            if (midiPath == null) {
                Toast.makeText(this, "MIDI 파일을 먼저 선택하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!midiPlaybackManager.isCurrentlyPlaying()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startPlayback()
            }
        }

        findViewById<ImageButton>(R.id.btnPause).setOnClickListener {
            stopPlayback()
        }

        findViewById<ImageButton>(R.id.btnStop).setOnClickListener {
            stopPlayback()
            midiPlaybackManager.seekTo(0)
            viewPager.setCurrentItem(0, false)
        }

        findViewById<ImageButton>(R.id.btnRewind).setOnClickListener {
            midiPlaybackManager.seekTo(midiPlaybackManager.getCurrentTime() - 10000)
        }

        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            midiPlaybackManager.seekTo(midiPlaybackManager.getCurrentTime() + 10000)
        }

        findViewById<ImageButton>(R.id.btnSelectMidi).setOnClickListener {
            openMidiFilePicker()
        }
    }

    private fun setupSeekBar() {
        midiSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            // 드래그 시작 전 상태 저장
            private var wasPlaying = false

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 실제 재생 중이면 일시정지하고, 그 상태를 기록
                wasPlaying = midiPlaybackManager.isCurrentlyPlaying()
                if (wasPlaying) {
                    stopPlayback()
                }
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 사용자가 슬라이더를 움직일 때만 위치 변경
                    midiPlaybackManager.seekTo(progress)
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 드래그를 끝낼 때, 애초에 재생 중이었던 상태면 재생 재개
                if (wasPlaying) {
                    startPlayback()
                }
            }
        })
    }

    private fun startPlayback() {
        val startDelaySec = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("start_delay_sec", 0)
        midiPlaybackManager.startPlayback(startDelaySec)
    }

    private fun stopPlayback() {
        midiPlaybackManager.stopPlayback()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateTimeDisplay(currentMillis: Int, totalMillis: Int) {
        val currentFormatted = formatMillis(currentMillis.toLong())
        val totalFormatted = formatMillis(totalMillis.toLong())
        midiSeekBar.progress = currentMillis

        if (midiPlaybackManager.isInCountdown()) {
            timeText.setTextColor(Color.RED)
        } else {
            timeText.setTextColor(Color.WHITE)
        }

        timeText.text = "$currentFormatted / $totalFormatted"
    }
    private fun showRestoreDialog() {
        // ① Companion object에서 불러온 PDF별 복원 정보 사용
        val pageToRestore  = restoredPage
        val millisToRestore = restoredMillis

        AlertDialog.Builder(this)
            .setTitle("이전 세션 복원")
            .setMessage("페이지 ${pageToRestore + 1}, 시간 ${formatMillis(millisToRestore.toLong())}로 복원하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                // ② 화면 복원
                viewPager.setCurrentItem(pageToRestore, false)
                midiPlaybackManager.seekTo(millisToRestore)

                // ③ 복원 선택 즉시 PDF별 상태 다시 저장
                pdfPath?.let {
                    val id = pdfId(it)
                    val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    prefs.edit().apply {
                        putInt("${id}_page",   pageToRestore)
                        putInt("${id}_millis", millisToRestore)
                        // MIDI 경로도 저장
                        putString("${id}_midiPath", midiPath)
                        apply()
                    }
                }
            }
            .setNegativeButton("아니오") { _, _ ->
                // 사용자가 '아니오' 선택 시, 글로벌 동기·지연 설정만 초기화
                syncPanelManager.clearPreferences()
            }
            .show()
    }

    private fun checkForMidiFile() {
        if (midiPath == null) {
            openMidiFilePicker()
            Toast.makeText(this, "연결된 MIDI 파일이 없습니다. 파일을 선택해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMidiFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/mid", "audio/x-midi"))
        }
        startActivityForResult(intent, PICK_MIDI_FILE)
    }

    private fun openMusicXmlFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/xml"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/xml", "text/xml"))
        }
        startActivityForResult(intent, PICK_MUSICXML_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_MIDI_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                handleMidiFileSelection(uri)
            }
        } else if (requestCode == PICK_MUSICXML_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                handleMusicXmlFileSelection(uri)
            }
        }
    }

    private fun handleMidiFileSelection(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri)
            if (!fileName.endsWith(".mid", ignoreCase = true)) {
                Toast.makeText(this, "올바른 MIDI 파일(.mid)을 선택하세요", Toast.LENGTH_SHORT).show()
                return
            }

            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("파일을 열 수 없습니다")

            val tempMidi = File.createTempFile("selected_midi", ".mid", cacheDir)
            tempMidi.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            // MIDI 파일 유효성 검사
            val midiHeader = MidiLoader.getMidiHeader(tempMidi)
            if (midiHeader == null) {
                throw IOException("지원되지 않는 MIDI 파일 형식입니다")
            }

            midiPath = tempMidi.absolutePath
            loadMidiFile()
            savePlaybackState()
        } catch (e: IOException) {
            Toast.makeText(this, "파일 처리 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "MIDI 파일을 불러오는 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadJsonForHighlight() {
        try {
            val musicXmlFile = File(musicXmlPath ?: return)
            var jsonText: String? = null

            // 1) 같은 디렉터리의 JSON 우선 시도
            val externalJson = File(musicXmlFile.parent, "everlasting_message.json")
            if (externalJson.isFile) {
                jsonText = externalJson.readText(Charsets.UTF_8)
                Log.d("PlayActivity", "Highlight JSON loaded from external file")
            } else {
                // 2) assets 내 메타데이터 파일 fallback
                try {
                    assets.open("everlasting_message_metadata.json").use { input ->
                        jsonText = input.bufferedReader(Charsets.UTF_8).readText()
                        Log.d("PlayActivity", "Highlight JSON loaded from assets")
                    }
                } catch (ignore: Exception) {
                    Log.w("PlayActivity", "Highlight JSON not found in assets")
                }
            }

            if (jsonText != null) {
                val jsonObject = JSONObject(jsonText)
                musicJsonData = jsonObject.optJSONObject("pages")
            } else {
                Log.w("PlayActivity", "Highlight JSON could not be loaded")
            }
        } catch (e: Exception) {
            Log.e("PlayActivity", "Error loading highlight JSON", e)
        }
    }

    private fun handleMusicXmlFileSelection(uri: Uri) {
        try {
            val tempXml = File(cacheDir, "temp_musicxml.xml")
            contentResolver.openInputStream(uri)?.use { input ->
                tempXml.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            musicXmlPath = tempXml.absolutePath
            pageChangeTimes = MusicXmlParser.parsePageChangeTimes(tempXml)
            currentLines = MusicXmlParser.parseLines(tempXml)
            loadJsonForHighlight()
            // 뷰가 레이아웃된 이후에 하이라이트 계산을 실행해야 정확한 좌표가 나옴
            annotationCanvas.post {
                currentLineIndex = -1
                updateCurrentLine(midiPlaybackManager.getCurrentTime())
            }
            Toast.makeText(this, "MusicXML 파일이 성공적으로 로드되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "MusicXML 파일 로드 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("PlayActivity", "Error loading MusicXML file", e)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "unknown.mid"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }

    private fun savePlaybackState() {
        pdfPath?.let {
           playbackState.saveStateForPdf(
               pdfPath       = it,
               page          = viewPager.currentItem,
               millis        = midiPlaybackManager.getCurrentTime(),
               midiPath      = midiPath,
               musicXmlPath  = musicXmlPath
           )
        }
    }

    override fun onPause() {
        super.onPause()
        savePlaybackState()
    }

    override fun onSupportNavigateUp(): Boolean {
        // 재생 중이면 중지
        if (midiPlaybackManager.isCurrentlyPlaying()) {
            stopPlayback()
        }
        // 화면 종료
        finish()
        return true
    }

    private fun formatMillis(ms: Long): String {
        val totalSec = ms / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        val sign = if (ms < 0) "-" else ""
        return String.format("%s%02d:%02d", sign, abs(minutes), abs(seconds))
    }

    private fun clearPdfSpecificSettings(prefs: SharedPreferences, pdfPath: String) {
        val pdfHash = pdfPath.hashCode()
        prefs.edit().apply {
            remove("sync_offset_ms_$pdfHash")
            remove("start_delay_sec_$pdfHash")
            apply()
        }
        syncOffsetInput.setText("")
        startDelayInput.setText("")
        startDelayInput.setHint("지연 시간(초)")
    }

    private fun loadPdfSpecificSettings(prefs: SharedPreferences, pdfPath: String) {
        val pdfHash = pdfPath.hashCode()
        val syncOffset = prefs.getInt("sync_offset_ms_$pdfHash", 0)
        val startDelay = prefs.getInt("start_delay_sec_$pdfHash", 0)
        syncOffsetInput.setText(syncOffset.toString())
        startDelayInput.setText(startDelay.toString())
    }

    private fun applyPdfSpecificSettings(prefs: SharedPreferences, pdfPath: String?) {
        pdfPath ?: return
        val pdfHash = pdfPath.hashCode()
        val syncValue = syncOffsetInput.text.toString().toIntOrNull()
        val delayValue = startDelayInput.text.toString().toIntOrNull()

        prefs.edit().apply {
            if (syncValue != null) putInt("sync_offset_ms_$pdfHash", syncValue)
            if (delayValue != null) putInt("start_delay_sec_$pdfHash", delayValue)
            apply()
        }

        Toast.makeText(this, "싱크 오프셋 및 시작 지연 설정이 적용되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 권한이 승인되면 파일 로드 재시도
                if (midiPath != null) loadMidiFile()
                if (musicXmlPath != null) {
                    val xmlFile = File(musicXmlPath!!)
                    pageChangeTimes = MusicXmlParser.parsePageChangeTimes(xmlFile)
                }
            } else {
                Toast.makeText(this, "파일 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateCurrentLine(currentMillis: Int) {
        if (currentLines.isEmpty()) {
            Log.i("PlayActivity", "currentLines is empty – highlight skipped")
            return
        }

        // 현재 재생 위치에 해당하는 라인 찾기
        val newLineIndex = currentLines.indexOfFirst { currentMillis in it.startTimeMs until it.endTimeMs }
        if (newLineIndex == -1) return

        val currentLine = currentLines[newLineIndex]

        // 라인 변경 시 PDF 하이라이트 갱신
        if (newLineIndex != currentLineIndex) {
            currentLineIndex = newLineIndex
            // 줄 전체 하이라이트 대신 마디 하이라이트만 표시하므로 PDFPagerAdapter의 line highlight는 제거
        }

        // annotationCanvas 가 아직 측정되지 않았다면 나중에 다시 시도
        if (annotationCanvas.height == 0) {
            annotationCanvas.postDelayed({ updateCurrentLine(currentMillis) }, 50)
            return
        }

        // 현재 라인에서 실제 재생 중인 마디 계산
        val currentTimeInLine = currentMillis - currentLine.startTimeMs
        val jsonData = musicJsonData ?: return
        val lineDesc = jsonData.optJSONObject(currentLine.pageNumber.toString())
            ?.optString("line ${currentLine.lineNumber + 1}") ?: return

        val durations = HighlightHelper.splitLineDuration(currentLine.startTimeMs, currentLine.endTimeMs, lineDesc)
        // 누적하여 위치 찾기
        var accum = 0
        var measureIdxInLine = 0
        for ((idx, d) in durations.withIndex()) {
            if (currentTimeInLine < accum + d) {
                measureIdxInLine = idx
                break
            }
            accum += d
        }

        val currentMeasure = currentLine.measureNumbers.getOrNull(measureIdxInLine) ?: return

        Log.i("PlayActivity", "🎵 Trying to highlight measure $currentMeasure")

        // PDF 원본 좌표계를 사용해야 AnnotationCanvas 매트릭스와 일치한다.
        val adapter = viewPager.adapter as? PDFPagerAdapter
        val adapterPageIndex = currentLine.pageNumber - 1
        val pageSize = adapter?.getPageSize(adapterPageIndex)

        if (pageSize == null) {
            // 페이지가 아직 렌더되지 않아 사이즈 정보가 없음 → 조금 뒤에 다시 시도
            annotationCanvas.postDelayed({ updateCurrentLine(currentMillis) }, 40)
            Log.d(
                "HighlightDebug",
                "page=${currentLine.pageNumber}[idx=$adapterPageIndex] bitmapSize=null (retry scheduled)"
            )
            return
        }

        val (pageWidthPx, pageHeightPx) = pageSize

        Log.d(
            "HighlightDebug",
            "page=${currentLine.pageNumber}[idx=$adapterPageIndex] bitmapSize=${pageSize} canvasSize=${annotationCanvas.width}x${annotationCanvas.height}"
        )

        val result = HighlightHelper.getMeasureHighlight(
            measureNumber = currentMeasure,
            lines = currentLines,
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx,
            jsonData = jsonData
        )

        if (result != null) {
            val (rect, durationMs) = result
            Log.i("PlayActivity", "🔔 Highlighting measure $currentMeasure for $durationMs ms")
            annotationCanvas.highlight(rect, durationMs)
        }
    }
}

