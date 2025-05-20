package com.example.scoreviewer

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
    private var pageCount = 0
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        val toolbar = findViewById<Toolbar>(R.id.playToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        midiSeekBar = findViewById(R.id.midiSeekBar)
        timeText = findViewById(R.id.txtCurrentTime)
        val pdfPath = intent.getStringExtra("pdfPath")
        val startPage = intent.getIntExtra("currentPage", 0)
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
            val pdfManager = PdfManager()
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
            Log.d("Playback", "Auto MIDI Load - file: ${midiFile.name}, size: ${midiFile.length()}, durationMillis: $durationMillis")

            if (durationMillis > 0) {
                totalMillis = durationMillis.toInt()
                currentMillis = 0

                midiSeekBar.max = totalMillis
                midiSeekBar.progress = currentMillis
                timeText.text = "00:00 / ${formatMillis(totalMillis.toLong())}"
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
                    currentMillis = progress  // 핵심: 사용자가 위치 옮기면 시간 갱신
                    timeText.text =
                        "${formatMillis(progress.toLong())} / ${formatMillis(totalMillis.toLong())}"
                    val newPage = (currentMillis.toFloat() / totalMillis * pageCount).toInt()
                    if (newPage < pageCount) {
                        viewPager.setCurrentItem(newPage, true)
                    }
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
            timeText.text = "00:00 / ${formatMillis(totalMillis.toLong())}"
            viewPager.setCurrentItem(0, true)
        }

        btnRewind.setOnClickListener {
            currentMillis = maxOf(0, currentMillis - 10000)
            midiSeekBar.progress = currentMillis
            timeText.text = "${formatMillis(currentMillis.toLong())} / ${formatMillis(totalMillis.toLong())}"
            viewPager.setCurrentItem((currentMillis.toFloat() / totalMillis * pageCount).toInt(), true)
        }

        btnForward.setOnClickListener {
            currentMillis = minOf(totalMillis, currentMillis + 10000)
            midiSeekBar.progress = currentMillis
            timeText.text = "${formatMillis(currentMillis.toLong())} / ${formatMillis(totalMillis.toLong())}"
            viewPager.setCurrentItem((currentMillis.toFloat() / totalMillis * pageCount).toInt(), true)
        }

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                // 아직 실제 오디오 재생은 없으므로 로그로만 출력
                Log.d("PlayActivity", "Volume: $progress%")
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun startPlaybackSimulation(seekBar: SeekBar, timeText: TextView) {
        isPlaying = true
        updateRunnable = object : Runnable {
            override fun run() {
                if (currentMillis > totalMillis || !isPlaying) {
                    stopPlayback()
                    return
                }
                seekBar.progress = currentMillis
                timeText.text = "${formatMillis(currentMillis.toLong())} / ${formatMillis(totalMillis.toLong())}"

                val nextPage = (currentMillis.toFloat() / totalMillis * pageCount).toInt()
                if (nextPage < pageCount) {
                    viewPager.setCurrentItem(nextPage, true)
                }

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
                Log.d("Playback", "Manual MIDI Load - durationMillis: $durationMillis, file: ${tempMidi.absolutePath}, size: ${tempMidi.length()}")

                if (durationMillis > 0) {
                    //초기화
                    totalMillis = durationMillis.toInt()
                    currentMillis = 0
                    midiSeekBar.max = totalMillis
                    midiSeekBar.progress = currentMillis
                    timeText.text = "00:00 / ${formatMillis(totalMillis.toLong())}"
                } else {
                    Toast.makeText(this, "MIDI 파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
