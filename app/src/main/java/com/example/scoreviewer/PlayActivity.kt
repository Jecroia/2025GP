package com.example.scoreviewer

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import java.io.File

class PlayActivity : AppCompatActivity() {

    private val PICK_MIDI_FILE = 2001

    private var isPlaying = false
    private var currentMillis = 0
    private var totalMillis = 0
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable
    private lateinit var viewPager: ViewPager2
    private lateinit var pdfManager: PdfManager
    private lateinit var midiSeekBar: SeekBar
    private lateinit var timeText: TextView
    private var currentMidiPath: String? = null
    private var pageCount = 0
    private var isPaused = false
    private var startPage = 0

    companion object {
        const val PREF_NAME = "PlaybackPrefs"
        const val KEY_PAGE = "last_page"
        const val KEY_MILLIS = "last_millis"
        const val KEY_MIDI_PATH = "last_midi_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        val toolbar = findViewById<Toolbar>(R.id.playToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        midiSeekBar = findViewById(R.id.midiSeekBar)
        timeText = findViewById(R.id.txtCurrentTime)
        val pdfPath = intent.getStringExtra("pdfPath")
        startPage = intent.getIntExtra("currentPage", 0)
        viewPager = findViewById(R.id.viewPager)
        pdfManager = PdfManager()

        //재생 바 밑 컨트롤 버튼
        val btnPlay = findViewById<ImageButton>(R.id.btnPlay)
        val btnPause = findViewById<ImageButton>(R.id.btnPause)
        val btnStop = findViewById<ImageButton>(R.id.btnStop)
        val btnRewind = findViewById<ImageButton>(R.id.btnRewind)
        val btnForward = findViewById<ImageButton>(R.id.btnForward)
        val volumeSeekBar = findViewById<SeekBar>(R.id.volumeSeekBar)

        // PDF 경로 받아오기
        if (pdfPath != null) {
            pdfManager = PdfManager()
            pdfManager.open(pdfPath)

            pageCount = pdfManager.pageCount()
            viewPager.adapter = PDFPagerAdapter(pdfManager, pdfManager.pageCount())
            viewPager.setCurrentItem(startPage, false)
        }

        // MIDI 파일 처리
        val midiPath = intent.getStringExtra("midiPath")
        if (midiPath != null) {
            val midiFile = File(midiPath)
            val durationMillis = MidiLoader.getMidiDurationMillis(midiFile)
            Log.d("PlayActivity", "Auto MIDI Load - file: ${midiFile.name}, durationMillis: $durationMillis")

            if (durationMillis > 0) {
                totalMillis = durationMillis.toInt()
                currentMillis = 0
                midiSeekBar.max = totalMillis
                midiSeekBar.progress = currentMillis
                updateTimeText()
            } else {
                Toast.makeText(this, "MIDI 파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        val btnSelectMidi = findViewById<ImageButton>(R.id.btnSelectMidi)
        btnSelectMidi.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/mid", "audio/x-midi"))
            }
            startActivityForResult(intent, PICK_MIDI_FILE)
        }

        handler = Handler(Looper.getMainLooper())

        btnPlay.setOnClickListener {
            if (totalMillis <= 0) {
                Toast.makeText(this, "먼저 MIDI 파일을 로드하세요", Toast.LENGTH_SHORT).show()
            } else if (!isPlaying) {
                startPlaybackSimulation(midiSeekBar, timeText)
            } else {
                Toast.makeText(this, "이미 재생 중입니다", Toast.LENGTH_SHORT).show()
            }
        }

        midiSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentMillis = progress
                    updateTimeText()
                    val newPage = (currentMillis.toFloat() / totalMillis * pageCount).toInt()
                    viewPager.setCurrentItem(newPage, true)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 재생 중에는 일시정지할 수도 있음
                if (isPlaying) stopPlayback()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 사용자가 손을 뗐을 때 다시 재생할지 말지는 원하는 동작에 따라 조정
                if (!isPlaying && totalMillis > 0) startPlaybackSimulation(midiSeekBar, timeText)
            }
        })

        btnPause.setOnClickListener {
            if (isPlaying) {
                isPaused = true
                stopPlayback()
            }
        }

        btnStop.setOnClickListener {
            stopPlayback()
            currentMillis = 0
            midiSeekBar.progress = currentMillis
            viewPager.setCurrentItem(0, true)
            updateTimeText()
        }

        btnRewind.setOnClickListener {
            currentMillis = maxOf(0, currentMillis - 10000)
            midiSeekBar.progress = currentMillis
            viewPager.setCurrentItem((currentMillis.toFloat() / totalMillis * pageCount).toInt(), true)
            updateTimeText()
        }

        btnForward.setOnClickListener {
            currentMillis = minOf(totalMillis, currentMillis + 10000)
            midiSeekBar.progress = currentMillis
            viewPager.setCurrentItem((currentMillis.toFloat() / totalMillis * pageCount).toInt(), true)
            updateTimeText()
        }

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                // 아직 실제 오디오 재생은 없으므로 로그로만 출력
                Log.d("PlayActivity", "Volume: $progress%")
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val savedPage = prefs.getInt(KEY_PAGE, -1)
        val savedMillis = prefs.getInt(KEY_MILLIS, -1)
        val savedMidiPath = prefs.getString(KEY_MIDI_PATH, null)

        if (savedPage != -1 && savedMillis != -1 && savedMidiPath != null) {
            AlertDialog.Builder(this)
                .setTitle("이전 세션 복원")
                .setMessage("이전 위치로 복원할까요?\n페이지: ${savedPage + 1}, 시간: ${formatMillis(savedMillis.toLong())}")
                .setPositiveButton("예") { _, _ ->
                    val file = File(savedMidiPath)
                    if (file.exists()) {
                        val duration = MidiLoader.getMidiDurationMillis(file)
                        if (duration > 0) {
                            totalMillis = duration.toInt()
                            midiSeekBar.max = totalMillis
                            currentMillis = savedMillis
                            midiSeekBar.progress = currentMillis
                            updateTimeText()
                            viewPager.setCurrentItem(savedPage, false)
                            currentMidiPath = savedMidiPath  // ← 중요!
                            Log.d("PlayActivity", "복원 성공: $savedMidiPath")
                        } else {
                            Toast.makeText(this, "복원된 MIDI를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "MIDI 파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("아니오", null)
                .show()
        }
    }

    private fun updateTimeText() {
        val currentFormatted = formatMillis(currentMillis.toLong())
        val totalFormatted = formatMillis(totalMillis.toLong())
        timeText.text = "$currentFormatted / $totalFormatted"
    }

    private fun savePlaybackState() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val midiPath = intent.getStringExtra("midiPath")
        prefs.edit().apply {
            putInt(KEY_PAGE, viewPager.currentItem)
            putInt(KEY_MILLIS, currentMillis)
            putString(KEY_MIDI_PATH, currentMidiPath)
            if (midiPath != null) putString(KEY_MIDI_PATH, midiPath) //경로 저장
            apply()
        }
    }

    private fun startPlaybackSimulation(seekBar: SeekBar, timeText: TextView) {
        isPlaying = true

        //화면 꺼짐 방지
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        updateRunnable = object : Runnable {
            override fun run() {
                if (currentMillis > totalMillis || !isPlaying) {
                    stopPlayback()
                    return
                }
                midiSeekBar.progress = currentMillis
                updateTimeText()
                val nextPage = (currentMillis.toFloat() / totalMillis * pageCount).toInt()
                viewPager.setCurrentItem(nextPage, true)
                currentMillis += 1000
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable)
    }


    private fun stopPlayback() {
        isPlaying = false
        handler.removeCallbacks(updateRunnable)
    }

    private fun formatMillis(ms: Long): String {
        val totalSec = ms / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format("%02d:%02d", minutes, seconds)
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

    override fun onPause() {
        super.onPause()
        savePlaybackState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_MIDI_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val fileName = getFileNameFromUri(uri)
                if (!fileName.endsWith(".mid", ignoreCase = true)) {
                    Toast.makeText(this, "올바른 MIDI 파일(.mid)을 선택하세요", Toast.LENGTH_SHORT).show()
                    return@let
                }

                val inputStream = contentResolver.openInputStream(uri)
                val tempMidi = File.createTempFile("selected_midi", ".mid", cacheDir)
                tempMidi.outputStream().use { output -> inputStream?.copyTo(output) }

                val durationMillis = MidiLoader.getMidiDurationMillis(tempMidi)
                Log.d("PlayActivity", "Manual MIDI Load - durationMillis: $durationMillis")

                if (durationMillis > 0) {
                    totalMillis = durationMillis.toInt()
                    currentMillis = 0
                    midiSeekBar.max = totalMillis
                    midiSeekBar.progress = currentMillis
                    updateTimeText()
                    savePlaybackState()
                } else {
                    Toast.makeText(this, "MIDI 파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        savePlaybackState()
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
