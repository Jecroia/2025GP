package com.example.scoreviewer

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

/**
 * MusicXML 파일을 파싱하여 페이지 전환 타임스탬프(ms) 리스트를 반환하는 유틸리티 클래스
 */
object MusicXmlParser {
    private const val TAG = "MusicXmlParser"

    data class Measure(
        val number: Int,
        val beats: Int,
        val beatType: Int,
        val tempo: Float,
        val divisions: Int = 1,
        val duration: Int = 0,
        val repeatStart: Boolean = false,
        val repeatEnd: Boolean = false,
        var repeatCount: Int = 1,
        val jumpTo: Int? = null,  // D.C., D.S. 등의 경우 이동할 마디 번호
        val jumpType: String? = null  // "D.C.", "D.S.", "Fine" 등
    )

    /**
     * @param xmlFile MusicXML 파일
     * @param measuresPerPage 한 페이지에 들어갈 마디 수(기본값 4)
     * @return 각 페이지별 전환 타임스탬프(ms) 리스트
     */
    fun parsePageChangeTimes(
        xmlFile: File,
        measuresPerPage: Int = 4
    ): List<Int> {
        val measures = parseMeasures(xmlFile)
        if (measures.isEmpty()) {
            Log.e(TAG, "No measures found in MusicXML file")
            return emptyList()
        }

        Log.d(TAG, "Parsed ${measures.size} measures")
        measures.forEachIndexed { index, measure ->
            Log.d(TAG, "Measure $index: tempo=${measure.tempo}, beats=${measure.beats}/${measure.beatType}, " +
                    "divisions=${measure.divisions}, duration=${measure.duration}, " +
                    "repeatStart=${measure.repeatStart}, repeatEnd=${measure.repeatEnd}, " +
                    "repeatCount=${measure.repeatCount}, jumpTo=${measure.jumpTo}, " +
                    "jumpType=${measure.jumpType}")
        }

        val times = mutableListOf<Int>()
        var currentTimeMs = 0
        var currentDivisions = 1
        var currentTempo = 120f
        var currentMeasureIndex = 0
        val processedMeasures = mutableSetOf<Int>()
        val repeatCounts = mutableMapOf<Int, Int>()  // 마디별 남은 반복 횟수 추적

        // 반복 구조를 포함한 실제 연주 순서대로 마디 처리
        while (currentMeasureIndex < measures.size) {
            if (processedMeasures.contains(currentMeasureIndex)) {
                // 이미 처리된 마디는 건너뛰기
                currentMeasureIndex++
                continue
            }

            val measure = measures[currentMeasureIndex]
            processedMeasures.add(currentMeasureIndex)

            // divisions 값이 변경되면 업데이트
            if (measure.divisions > 0) {
                currentDivisions = measure.divisions
            }
            
            // 템포가 변경되면 업데이트
            if (measure.tempo > 0) {
                currentTempo = measure.tempo
            }

            // 마디의 실제 지속 시간 계산
            val beatDurationMs = (60_000 / currentTempo).roundToInt()
            val measureDurationMs = if (measure.duration > 0) {
                (measure.duration * beatDurationMs) / (currentDivisions * measure.beatType)
            } else {
                measure.beats * beatDurationMs
            }

            Log.d(TAG, "Processing measure ${currentMeasureIndex + 1}: duration=$measureDurationMs ms " +
                    "(tempo=$currentTempo, divisions=$currentDivisions, " +
                    "beats=${measure.beats}/${measure.beatType})")

            currentTimeMs += measureDurationMs

            // 페이지 전환이 필요한 시점에 타임스탬프 추가
            if ((processedMeasures.size % measuresPerPage == 0) || 
                (currentMeasureIndex == measures.size - 1 && !measure.repeatEnd)) {
                times.add(currentTimeMs)
                Log.d(TAG, "Page transition at measure ${currentMeasureIndex + 1}: ${currentTimeMs}ms")
            }

            // 반복 처리
            when {
                // D.C. 또는 D.S. 처리
                measure.jumpTo != null -> {
                    Log.d(TAG, "Jump to measure ${measure.jumpTo} (${measure.jumpType})")
                    currentMeasureIndex = measure.jumpTo - 1
                }
                // 일반 반복 처리
                measure.repeatEnd -> {
                    val repeatStartIndex = findRepeatStart(measures, currentMeasureIndex)
                    if (repeatStartIndex >= 0) {
                        val remainingCount = repeatCounts.getOrPut(repeatStartIndex) { measure.repeatCount } - 1
                        if (remainingCount > 0) {
                            Log.d(TAG, "Repeat from measure ${repeatStartIndex + 1} ($remainingCount times left)")
                            repeatCounts[repeatStartIndex] = remainingCount
                            currentMeasureIndex = repeatStartIndex
                        } else {
                            Log.d(TAG, "Repeat completed for measure ${repeatStartIndex + 1}")
                            repeatCounts.remove(repeatStartIndex)
                            currentMeasureIndex++
                        }
                    } else {
                        currentMeasureIndex++
                    }
                }
                else -> currentMeasureIndex++
            }
        }

        return times
    }

    /**
     * 반복 시작 마디 찾기
     */
    private fun findRepeatStart(measures: List<Measure>, currentIndex: Int): Int {
        for (i in currentIndex downTo 0) {
            if (measures[i].repeatStart) {
                return i
            }
        }
        return -1
    }

    /**
     * MusicXML 파일에서 마디 정보를 파싱
     */
    private fun parseMeasures(xmlFile: File): List<Measure> {
        val measures = mutableListOf<Measure>()
        var currentTempo = 120f
        var currentBeats = 4
        var currentBeatType = 4
        var currentDivisions = 1
        var measureNumber = 0
        var currentDuration = 0
        var currentRepeatStart = false
        var currentRepeatEnd = false
        var currentRepeatCount = 1
        var currentJumpTo: Int? = null
        var currentJumpType: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            
            FileInputStream(xmlFile).use { inputStream ->
                parser.setInput(inputStream, null)
                var eventType = parser.eventType
                
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "measure" -> {
                                    measureNumber++
                                    measures.add(Measure(
                                        measureNumber,
                                        currentBeats,
                                        currentBeatType,
                                        currentTempo,
                                        currentDivisions,
                                        currentDuration,
                                        currentRepeatStart,
                                        currentRepeatEnd,
                                        currentRepeatCount,
                                        currentJumpTo,
                                        currentJumpType
                                    ))
                                    // 마디 시작 시 상태 초기화
                                    currentDuration = 0
                                    currentRepeatStart = false
                                    currentRepeatEnd = false
                                    currentRepeatCount = 1
                                    currentJumpTo = null
                                    currentJumpType = null
                                }
                                "sound" -> {
                                    val tempoAttr = parser.getAttributeValue(null, "tempo")
                                    if (tempoAttr != null) {
                                        currentTempo = tempoAttr.toFloatOrNull() ?: currentTempo
                                        Log.d(TAG, "Found tempo change: $currentTempo")
                                    }
                                }
                                "time" -> {
                                    var beats = currentBeats
                                    var beatType = currentBeatType
                                    
                                    while (eventType != XmlPullParser.END_TAG || parser.name != "time") {
                                        eventType = parser.next()
                                        if (eventType == XmlPullParser.START_TAG) {
                                            when (parser.name) {
                                                "beats" -> beats = parser.nextText().toIntOrNull() ?: beats
                                                "beat-type" -> beatType = parser.nextText().toIntOrNull() ?: beatType
                                            }
                                        }
                                    }
                                    
                                    currentBeats = beats
                                    currentBeatType = beatType
                                    Log.d(TAG, "Found time signature: $currentBeats/$currentBeatType")
                                }
                                "divisions" -> {
                                    currentDivisions = parser.nextText().toIntOrNull() ?: currentDivisions
                                    Log.d(TAG, "Found divisions: $currentDivisions")
                                }
                                "duration" -> {
                                    currentDuration = parser.nextText().toIntOrNull() ?: currentDuration
                                }
                                "repeat" -> {
                                    val direction = parser.getAttributeValue(null, "direction")
                                    when (direction) {
                                        "forward" -> currentRepeatStart = true
                                        "backward" -> {
                                            currentRepeatEnd = true
                                            val times = parser.getAttributeValue(null, "times")
                                            if (times != null) {
                                                currentRepeatCount = times.toIntOrNull() ?: 1
                                            }
                                        }
                                    }
                                    Log.d(TAG, "Found repeat: direction=$direction, count=$currentRepeatCount")
                                }
                                "coda" -> {
                                    currentJumpType = "Coda"
                                    Log.d(TAG, "Found coda")
                                }
                                "segno" -> {
                                    currentJumpType = "D.S."
                                    Log.d(TAG, "Found segno")
                                }
                                "fine" -> {
                                    currentJumpType = "Fine"
                                    Log.d(TAG, "Found fine")
                                }
                                "dacapo" -> {
                                    currentJumpType = "D.C."
                                    currentJumpTo = 1  // 첫 마디로 이동
                                    Log.d(TAG, "Found D.C.")
                                }
                                "dalsegno" -> {
                                    currentJumpType = "D.S."
                                    // 이전 segno 마디 찾기
                                    for (i in measures.size - 1 downTo 0) {
                                        if (measures[i].jumpType == "D.S.") {
                                            currentJumpTo = measures[i].number
                                            break
                                        }
                                    }
                                    Log.d(TAG, "Found D.S. to measure $currentJumpTo")
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MusicXML file", e)
            e.printStackTrace()
        }

        return measures
    }
} 