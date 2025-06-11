package com.example.scoreviewer

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MidiPlaybackManager(
    private val context: Context,
    private val pageCount: Int,
    private val onPageTransition: (Int) -> Unit,
    private val onTimeUpdate: (Int, Int) -> Unit
) {
    sealed class MidiError : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)

        class FileNotFound(file: File) : MidiError("MIDI 파일을 찾을 수 없습니다: ${file.name}")
        class InvalidFormat(file: File) : MidiError("잘못된 MIDI 파일 형식입니다: ${file.name}")
        class InvalidData(file: File, cause: Throwable) : 
            MidiError("MIDI 데이터가 손상되었습니다: ${file.name}", cause)
        class SystemError(cause: Throwable) : 
            MidiError("MIDI 시스템 오류: ${cause.localizedMessage ?: "알 수 없는 오류"}", cause)
        class EmptySequence : MidiError("MIDI 파일에 트랙이 없습니다")
        class InvalidDuration : MidiError("MIDI 파일의 재생 시간을 계산할 수 없습니다")
    }

    private var currentMillis: Int = 0
    private var totalMillis: Int = 0
    private var isPlaying: Boolean = false
    private var isCountdownActive: Boolean = false
    private var countdownSeconds: Int = 0
    private var currentMeasure: Int = 1
    private var scoreMetadata: ScoreMetadata? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateRunnable: Runnable
    private lateinit var countdownRunnable: Runnable
    private lateinit var midiManager: MidiManager
    private var midiDevice: MidiDevice? = null
    private var midiInputPort: MidiInputPort? = null
    private var pageTransitionEvents: List<PageTransitionEvent> = emptyList()
    private var midiHeader: MidiLoader.MidiHeader? = null
    private var lastPage = -1  // 마지막으로 전환된 페이지 추적
    private var syncOffset: Int = 0  // 싱크 오프셋 (밀리초)

    data class PageTransitionEvent(
        val timestamp: Long,  // MIDI 틱 기준 시간
        val pageNumber: Int,  // 전환할 페이지 번호
        val eventType: String // 이벤트 타입 (템포 변경, 마커 등)
    )

    init {
        midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    }

    fun initialize(midiFile: File, totalPages: Int) {
        try {
            validateMidiFile(midiFile)
            midiHeader = loadMidiHeader(midiFile)
            validateMidiHeader(midiHeader)
            
            totalMillis = MidiLoader.getMidiDurationMillis(midiFile).toInt()
            if (totalMillis <= 0) {
                throw MidiError.InvalidDuration()
            }
            
            loadMidiEvents(midiFile)
        } catch (e: MidiError) {
            Log.e("MidiPlaybackManager", "MIDI 초기화 실패", e)
            throw e
        } catch (e: Exception) {
            val error = MidiError.SystemError(e)
            Log.e("MidiPlaybackManager", "MIDI 초기화 실패", e)
            throw error
        }
    }

    private fun validateMidiFile(file: File) {
        if (!file.exists()) {
            throw MidiError.FileNotFound(file)
        }
        if (!file.name.endsWith(".mid", ignoreCase = true)) {
            throw MidiError.InvalidFormat(file)
        }
        if (file.length() == 0L) {
            throw MidiError.InvalidFormat(file)
        }
    }

    private fun loadMidiHeader(file: File): MidiLoader.MidiHeader? {
        return MidiLoader.getMidiHeader(file) ?: throw MidiError.InvalidFormat(file)
    }

    private fun validateMidiHeader(header: MidiLoader.MidiHeader?) {
        if (header == null || header.numTracks == 0) {
            throw MidiError.EmptySequence()
        }
    }

    fun startPlayback(startDelaySec: Int = 0) {
        if (startDelaySec > 0) {
            startCountdown(startDelaySec)
        } else {
            startActualPlayback()
        }
    }

    fun stopPlayback() {
        isPlaying = false
        isCountdownActive = false
        handler.removeCallbacks(updateRunnable)
        if (::countdownRunnable.isInitialized) {
            handler.removeCallbacks(countdownRunnable)
        }
    }

    fun seekTo(millis: Int) {
        currentMillis = millis.coerceIn(0, totalMillis)
        
        // 현재 시간에 해당하는 마디 찾기 (싱크 오프셋 고려)
        val targetMeasure = findMeasureForTime(millis - syncOffset)
        currentMeasure = targetMeasure
        
        // 해당 마디의 페이지로 이동
        scoreMetadata?.getPageForMeasure(targetMeasure)?.let { page ->
            onPageTransition(page)
        }
        
        updatePlaybackState()
    }

    fun getCurrentTime(): Int = currentMillis
    fun getTotalTime(): Int = totalMillis
    fun isCurrentlyPlaying(): Boolean = isPlaying
    fun isInCountdown(): Boolean = isCountdownActive

    private fun startCountdown(seconds: Int) {
        isCountdownActive = true
        countdownSeconds = seconds
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (countdownSeconds > 0) {
                    onTimeUpdate(-countdownSeconds * 1000, totalMillis)
                    countdownSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    isCountdownActive = false
                    startActualPlayback()
                }
            }
        }
        handler.post(countdownRunnable)
    }

    private fun startActualPlayback() {
        isPlaying = true
        updateRunnable = object : Runnable {
            override fun run() {
                if (!isPlaying || currentMillis > totalMillis) {
                    stopPlayback()
                    return
                }
                updatePlaybackState()
                currentMillis += 1000
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable)
    }

    private fun updatePlaybackState() {
        if (!isPlaying) return
        
        // 현재 마디에 해당하는 페이지로 전환
        scoreMetadata?.getPageForMeasure(currentMeasure)?.let { targetPage ->
            onPageTransition(targetPage)
        }
        
        // 다음 마디로 진행
        currentMeasure++
        
        // BPM에 따른 시간 업데이트
        val bpm = scoreMetadata?.getBpm() ?: 120f
        val millisPerMeasure = (60000f / bpm * 4).toInt() // 4/4 박자 기준
        currentMillis += millisPerMeasure
        
        // 싱크 오프셋은 한 번만 적용
        if (currentMeasure == 1) {
            currentMillis += syncOffset
        }
        
        onTimeUpdate(currentMillis, totalMillis)
    }

    private fun loadMidiEvents(midiFile: File) {
        try {
            // MIDI 파일 정보 로깅
            Log.d("MidiPlaybackManager", "MIDI 파일 로드 시작: ${midiFile.name}")
            Log.d("MidiPlaybackManager", "총 페이지 수: $pageCount, 총 재생 시간: $totalMillis ms")
            
            // MIDI 이벤트는 더 이상 페이지 전환에 사용하지 않음
            pageTransitionEvents = emptyList()
            
        } catch (e: Exception) {
            Log.e("MidiPlaybackManager", "MIDI 파일 로드 실패", e)
            throw e
        }
    }

    fun setScoreMetadata(metadata: ScoreMetadata) {
        scoreMetadata = metadata
    }

    private fun findMeasureForTime(millis: Int): Int {
        val bpm = scoreMetadata?.getBpm() ?: 120f
        val millisPerMeasure = (60000f / bpm * 4).toInt() // 4/4 박자 기준
        return (millis / millisPerMeasure).toInt() + 1
    }

    fun setSyncOffset(offset: Int) {
        syncOffset = offset
        // 현재 재생 중이라면 즉시 페이지 전환 적용
        if (isPlaying) {
            updatePlaybackState()
        }
    }

    fun getSyncOffset(): Int = syncOffset
} 