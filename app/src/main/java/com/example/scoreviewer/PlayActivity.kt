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

    companion object {
        const val PREF_NAME = "PlaybackPrefs"
        const val KEY_PAGE = "last_page"
        const val KEY_MILLIS = "last_millis"
        const val KEY_PDF_PATH = "last_pdf"
        const val KEY_MIDI_PATH = "last_midi"
    }

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

    private var pdfPath: String? = null
    private var midiPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        val toolbar = findViewById<Toolbar>(R.id.playToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        handler = Handler(Looper.getMainLooper())
        midiSeekBar = findViewById(R.id.midiSeekBar)
        timeText = findViewById(R.id.txtCurrentTime)
        viewPager = findViewById(R.id.viewPager)
        pdfManager = PdfManager()

        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val savedPage = prefs.getInt(KEY_PAGE, -1)
        val savedMillis = prefs.getInt(KEY_MILLIS, -1)
        val savedPdfPath = prefs.getString(KEY_PDF_PATH, null)
        val savedMidiPath = prefs.getString(KEY_MIDI_PATH, null)

        pdfPath = intent.getStringExtra("pdfPath") ?: savedPdfPath
        midiPath = intent.getStringExtra("midiPath") ?: savedMidiPath

        if (pdfPath != null) {
            pdfManager.open(pdfPath!!)
            pageCount = pdfManager.pageCount()
            viewPager.adapter = PDFPagerAdapter(pdfManager, pageCount)
        }

        if (midiPath != null) {
            val midiFile = File(midiPath!!)
            val duration = MidiLoader.getMidiDurationMillis(midiFile)
            if (duration > 0) {
                totalMillis = duration.toInt()
                midiSeekBar.max = totalMillis
                Log.d("PlayActivity", "MIDI Loaded: $midiPath")
            }
        }

        val btnPlay = findViewById<ImageButton>(R.id.btnPlay)
        val btnPause = findViewById<ImageButton>(R.id.btnPause)
        val btnStop = findViewById<ImageButton>(R.id.btnStop)
        val btnRewind = findViewById<ImageButton>(R.id.btnRewind)
        val btnForward = findViewById<ImageButton>(R.id.btnForward)
        val btnSelectMidi = findViewById<ImageButton>(R.id.btnSelectMidi)
        val volumeSeekBar = findViewById<SeekBar>(R.id.volumeSeekBar)

        btnPlay.setOnClickListener {
            if (!isPlaying && totalMillis > 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                startPlaybackSimulation()
            }
        }

        btnPause.setOnClickListener { stopPlayback() }

        btnStop.setOnClickListener {
            stopPlayback()
            currentMillis = 0
            midiSeekBar.progress = 0
            timeText.text = "00:00 / ${formatMillis(totalMillis.toLong())}"
            viewPager.setCurrentItem(0, false)
        }

        btnRewind.setOnClickListener {
            currentMillis = maxOf(0, currentMillis - 10000)
            updateSeekUI()
        }

        btnForward.setOnClickListener {
            currentMillis = minOf(totalMillis, currentMillis + 10000)
            updateSeekUI()
        }

        btnSelectMidi.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/midi", "audio/mid", "audio/x-midi"))
            }
            startActivityForResult(intent, PICK_MIDI_FILE)
        }

        midiSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentMillis = progress
                    updateSeekUI()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { stopPlayback() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { startPlaybackSimulation() }
        })

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                Log.d("PlayActivity", "Volume: $progress%")
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        if (savedPage != -1 && savedMillis != -1 && savedPdfPath != null && savedMidiPath != null) {
            AlertDialog.Builder(this)
                .setTitle("이전 세션 복원")
                .setMessage("페이지 ${savedPage + 1}, 시간 ${formatMillis(savedMillis.toLong())}로 복원하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    viewPager.setCurrentItem(savedPage, false)
                    currentMillis = savedMillis
                    midiSeekBar.progress = currentMillis
                    updateSeekUI()
                }
                .setNegativeButton("아니오", null)
                .show()
        }
    }

    private fun startPlaybackSimulation() {
        isPlaying = true
        updateRunnable = object : Runnable {
            override fun run() {
                if (!isPlaying || currentMillis > totalMillis) {
                    stopPlayback()
                    return
                }
                updateSeekUI()
                currentMillis += 1000
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable)
    }

    private fun stopPlayback() {
        isPlaying = false
        handler.removeCallbacks(updateRunnable)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateSeekUI() {
        val currentFormatted = formatMillis(currentMillis.toLong())
        val totalFormatted = formatMillis(totalMillis.toLong())
        midiSeekBar.progress = currentMillis
        timeText.text = "$currentFormatted / $totalFormatted"
        val page = (currentMillis.toFloat() / totalMillis * pageCount).toInt()
        viewPager.setCurrentItem(page, true)
    }

    private fun savePlaybackState() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_PAGE, viewPager.currentItem)
            putInt(KEY_MILLIS, currentMillis)
            putString(KEY_PDF_PATH, pdfPath)
            putString(KEY_MIDI_PATH, midiPath)
            apply()
        }
    }

    override fun onPause() {
        super.onPause()
        savePlaybackState()
    }

    override fun onSupportNavigateUp(): Boolean {
        savePlaybackState()
        onBackPressedDispatcher.onBackPressed()
        return true
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
                if (durationMillis > 0) {
                    midiPath = tempMidi.absolutePath
                    totalMillis = durationMillis.toInt()
                    currentMillis = 0
                    midiSeekBar.max = totalMillis
                    updateSeekUI()
                    savePlaybackState()
                } else {
                    Toast.makeText(this, "MIDI 파일을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

