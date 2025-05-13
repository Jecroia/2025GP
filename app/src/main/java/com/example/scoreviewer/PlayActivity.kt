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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        val toolbar = findViewById<Toolbar>(R.id.playToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pdfPath = intent.getStringExtra("pdfPath")
        val startPage = intent.getIntExtra("currentPage", 0)

        // PDF 경로 받아오기
        if (pdfPath != null) {
            val pdfManager = PdfManager()
            pdfManager.open(pdfPath)

            val viewPager = findViewById<ViewPager2>(R.id.viewPager)
            viewPager.adapter = PDFPagerAdapter(pdfManager, pdfManager.pageCount())
            viewPager.setCurrentItem(startPage, false)
        }

        val midiSeekBar = findViewById<SeekBar>(R.id.midiSeekBar)
        val timeText = findViewById<TextView>(R.id.txtCurrentTime)

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
        //재생 바 밑 컨트롤 버튼
        val btnPlay = findViewById<ImageButton>(R.id.btnPlay)
        val btnStop = findViewById<ImageButton>(R.id.btnStop)
        val btnRewind = findViewById<ImageButton>(R.id.btnRewind)
        val btnForward = findViewById<ImageButton>(R.id.btnForward)
        val volumeSeekBar = findViewById<SeekBar>(R.id.volumeSeekBar)

        handler = Handler(Looper.getMainLooper())

        btnPlay.setOnClickListener {
            if (totalMillis <= 0) {
                Toast.makeText(this, "먼저 MIDI 파일을 로드하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isPlaying) {
                startPlaybackSimulation(midiSeekBar, timeText)
            } else {
                Toast.makeText(this, "이미 재생 중입니다", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            stopPlayback()
        }

        btnRewind.setOnClickListener {
            currentMillis = 0
            midiSeekBar.progress = currentMillis
            timeText.text = "${formatMillis(currentMillis.toLong())} / ${formatMillis(totalMillis.toLong())}"
        }

        btnForward.setOnClickListener {
            currentMillis = totalMillis
            midiSeekBar.progress = currentMillis
            timeText.text = "${formatMillis(currentMillis.toLong())} / ${formatMillis(totalMillis.toLong())}"
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
                if (currentMillis <= totalMillis && isPlaying) {
                    seekBar.progress = currentMillis
                    timeText.text = "${formatMillis(currentMillis.toLong())} / ${formatMillis(totalMillis.toLong())}"
                    currentMillis += 1000
                    handler.postDelayed(this, 1000)
                }
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

                    val seekBar = findViewById<SeekBar>(R.id.midiSeekBar)
                    val timeText = findViewById<TextView>(R.id.txtCurrentTime)
                    seekBar.max = totalMillis
                    seekBar.progress = currentMillis
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
