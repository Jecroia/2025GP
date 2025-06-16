package com.example.scoreviewer

import android.content.Context
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

data class Line(
    val startTimeMs: Int,
    val endTimeMs: Int,
    val pageNumber: Int
)

class MusicXmlParser(private val context: Context) {
    private var currentPage = 1
    private var totalPages = 1
    private var lineCount = 5  // 기본값으로 5줄 설정
    private var actualLineCount = 5  // 실제 내용이 있는 줄 수
    private var pageChangeTimes: List<Int> = emptyList()
    private var currentLines: List<Line> = emptyList()

    fun setPage(page: Int) {
        if (page in 1..totalPages) {
            currentPage = page
        }
    }

    fun getPageMargins(): Pair<Float, Float> {
        // 기본 마진 값 반환 (상단 10%, 하단 10%)
        return Pair(10f, 10f)
    }

    fun getLineCount(): Int {
        return lineCount
    }

    fun getActualLineCount(): Int {
        return actualLineCount
    }

    fun setTotalPages(pages: Int) {
        totalPages = pages
    }

    fun setLineCount(count: Int) {
        lineCount = count
    }

    fun setActualLineCount(count: Int) {
        actualLineCount = count
    }

    fun parsePageChangeTimes(xmlFile: File): List<Int> {
        val measures = parseMeasures(xmlFile)
        if (measures.isEmpty()) {
            Log.e("MusicXmlParser", "No measures found in MusicXML file")
            return emptyList()
        }

        // 각 마디의 지속 시간 계산
        val measureTimings = measures.map { measure ->
            val duration = if (measure.duration > 0) {
                ((measure.duration.toDouble() * 60_000) / (measure.divisions * measure.tempo)).roundToInt()
            } else {
                (measure.beats * 60_000 / measure.tempo).roundToInt()
            }
            Log.d("MusicXmlParser", "Measure ${measure.number}: duration=$duration ms (tempo=${measure.tempo}, " +
                    "divisions=${measure.divisions}, duration=${measure.duration}, beats=${measure.beats})")
            duration
        }

        // 페이지별 시작 마디 번호 수집
        val pageMeasureStartNumbers = mutableListOf(0)  // 첫 페이지는 0번 마디부터 시작
        var currentPage = measures[0].pageNumber
        measures.forEachIndexed { index, measure ->
            if (measure.pageNumber != currentPage) {
                pageMeasureStartNumbers.add(index)
                currentPage = measure.pageNumber
                Log.d("MusicXmlParser", "Page change detected at measure ${measure.number} (index=$index)")
            }
        }

        // 반복 구간 처리
        val processedMeasures = mutableSetOf<Int>()
        val repeatCounts = mutableMapOf<Int, Int>()
        var currentMeasureIndex = 0
        var totalTimeMs = 0
        val pageChangeTimings = mutableListOf<Int>()

        while (currentMeasureIndex < measures.size) {
            if (processedMeasures.contains(currentMeasureIndex)) {
                currentMeasureIndex++
                continue
            }

            val measure = measures[currentMeasureIndex]
            processedMeasures.add(currentMeasureIndex)
            totalTimeMs += measureTimings[currentMeasureIndex]

            // 페이지 전환 시점 체크
            if (pageMeasureStartNumbers.contains(currentMeasureIndex + 1)) {
                pageChangeTimings.add(totalTimeMs)
                Log.d("MusicXmlParser", "Page transition timing: $totalTimeMs ms at measure ${measure.number}")
            }

            // 반복 처리
            when {
                measure.jumpTo != null -> {
                    Log.d("MusicXmlParser", "Jump to measure ${measure.jumpTo} (${measure.jumpType})")
                    currentMeasureIndex = measure.jumpTo - 1
                }
                measure.repeatEnd -> {
                    val repeatStartIndex = findRepeatStart(measures, currentMeasureIndex)
                    if (repeatStartIndex >= 0) {
                        val remainingCount = repeatCounts.getOrPut(repeatStartIndex) { measure.repeatCount } - 1
                        if (remainingCount > 0) {
                            Log.d("MusicXmlParser", "Repeat from measure ${repeatStartIndex + 1} ($remainingCount times left)")
                            repeatCounts[repeatStartIndex] = remainingCount
                            currentMeasureIndex = repeatStartIndex
                        } else {
                            Log.d("MusicXmlParser", "Repeat completed for measure ${repeatStartIndex + 1}")
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

        Log.d("MusicXmlParser", "Final page change timings: $pageChangeTimings")
        return pageChangeTimings
    }

    fun parseLines(xmlFile: File): List<Line> {
        val measures = parseMeasures(xmlFile)
        if (measures.isEmpty()) {
            Log.e("MusicXmlParser", "No measures found in MusicXML file")
            return emptyList()
        }

        val lines = mutableListOf<Line>()
        var currentTimeMs = 0
        var currentPage = 0
        var currentLine = 0
        var currentDivisions = 1
        var currentTempo = 120f
        var currentMeasureIndex = 0
        val processedMeasures = mutableSetOf<Int>()
        val repeatCounts = mutableMapOf<Int, Int>()
        var currentLineMeasures = mutableListOf<Int>()
        var lineStartTime = 0

        // 페이지별 마디 수 계산
        val measuresPerPage = mutableMapOf<Int, Int>()
        var currentPageMeasures = 0
        var currentPageNumber = 0

        // 먼저 페이지별 마디 수를 계산
        measures.forEach { measure ->
            if (measure.pageNumber != currentPageNumber) {
                measuresPerPage[currentPageNumber] = currentPageMeasures
                currentPageNumber = measure.pageNumber
                currentPageMeasures = 0
            }
            currentPageMeasures++
        }
        measuresPerPage[currentPageNumber] = currentPageMeasures

        // 각 페이지의 마디를 줄로 나누기
        currentPageNumber = 0
        currentPageMeasures = 0
        var measuresInCurrentLine = 0

        while (currentMeasureIndex < measures.size) {
            val measure = measures[currentMeasureIndex]
            if (measure.number in processedMeasures) {
                currentMeasureIndex++
                continue
            }

            processedMeasures.add(measure.number)
            currentDivisions = measure.divisions
            currentTempo = measure.tempo

            // 마디의 지속 시간을 밀리초로 변환
            val measureDurationMs = if (measure.duration > 0) {
                ((measure.duration.toDouble() * 60_000) / (currentDivisions * currentTempo)).roundToInt()
            } else {
                (measure.beats * 60_000 / currentTempo).roundToInt()
            }
            
            Log.d("MusicXmlParser", "Measure ${measure.number}: tempo=$currentTempo BPM, divisions=$currentDivisions, " +
                    "duration=$measureDurationMs ms (duration=${measure.duration}, beats=${measure.beats})")
            
            currentLineMeasures.add(measure.number)
            measuresInCurrentLine++
            currentPageMeasures++

            // 페이지가 바뀌면 새로운 줄 시작
            if (measure.pageNumber != currentPageNumber) {
                if (currentLineMeasures.isNotEmpty()) {
                    lines.add(Line(
                        startTimeMs = lineStartTime,
                        endTimeMs = currentTimeMs,
                        pageNumber = currentPageNumber,
                        lineNumber = currentLine,
                        measureNumbers = currentLineMeasures.toList()
                    ))
                }
                currentPageNumber = measure.pageNumber
                currentLine = 0
                currentLineMeasures.clear()
                measuresInCurrentLine = 0
                lineStartTime = currentTimeMs
            }
            // 현재 페이지의 마디 수에 따라 줄 나누기
            else if (measuresInCurrentLine >= 4) {  // 한 줄에 4마디씩
                lines.add(Line(
                    startTimeMs = lineStartTime,
                    endTimeMs = currentTimeMs + measureDurationMs,
                    pageNumber = currentPageNumber,
                    lineNumber = currentLine,
                    measureNumbers = currentLineMeasures.toList()
                ))

                currentTimeMs += measureDurationMs
                lineStartTime = currentTimeMs
                currentLine++
                measuresInCurrentLine = 0
                currentLineMeasures.clear()
            } else {
                currentTimeMs += measureDurationMs
            }

            when {
                measure.jumpTo != null -> {
                    currentMeasureIndex = measure.jumpTo - 1
                }
                measure.repeatEnd -> {
                    val repeatStartIndex = findRepeatStart(measures, currentMeasureIndex)
                    if (repeatStartIndex >= 0) {
                        val remainingCount = repeatCounts.getOrPut(repeatStartIndex) { measure.repeatCount } - 1
                        if (remainingCount > 0) {
                            repeatCounts[repeatStartIndex] = remainingCount
                            currentMeasureIndex = repeatStartIndex
                        } else {
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

        // 마지막 줄 처리
        if (currentLineMeasures.isNotEmpty()) {
            lines.add(Line(
                startTimeMs = lineStartTime,
                endTimeMs = currentTimeMs,
                pageNumber = currentPageNumber,
                lineNumber = currentLine,
                measureNumbers = currentLineMeasures.toList()
            ))
        }

        Log.d("MusicXmlParser", "Parsed ${lines.size} lines with total duration ${currentTimeMs}ms")
        Log.d("MusicXmlParser", "Measures per page: $measuresPerPage")
        return lines
    }

    companion object {
        fun parsePageChangeTimes(xmlFile: File): List<Int> {
            // TODO: 실제 MusicXML 파일 파싱 구현
            return emptyList()
        }

        fun parseLines(xmlFile: File): List<Line> {
            // TODO: 실제 MusicXML 파일 파싱 구현
            return emptyList()
        }
    }

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
        var currentPageNumber = 0

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
                                    // 페이지 번호 파싱
                                    val pageAttr = parser.getAttributeValue(null, "page")
                                    if (pageAttr != null) {
                                        currentPageNumber = pageAttr.toIntOrNull() ?: currentPageNumber
                                    }
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
                                        currentJumpType,
                                        currentPageNumber
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
                                        Log.d("MusicXmlParser", "Found tempo change: $currentTempo BPM")
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
                                    Log.d("MusicXmlParser", "Found time signature: $currentBeats/$currentBeatType")
                                }
                                "divisions" -> {
                                    currentDivisions = parser.nextText().toIntOrNull() ?: currentDivisions
                                    Log.d("MusicXmlParser", "Found divisions: $currentDivisions")
                                }
                                "duration" -> {
                                    currentDuration = parser.nextText().toIntOrNull() ?: currentDuration
                                    Log.d("MusicXmlParser", "Found duration: $currentDuration divisions")
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
                                    Log.d("MusicXmlParser", "Found repeat: direction=$direction, count=$currentRepeatCount")
                                }
                                "coda" -> {
                                    currentJumpType = "Coda"
                                    Log.d("MusicXmlParser", "Found coda")
                                }
                                "segno" -> {
                                    currentJumpType = "D.S."
                                    Log.d("MusicXmlParser", "Found segno")
                                }
                                "fine" -> {
                                    currentJumpType = "Fine"
                                    Log.d("MusicXmlParser", "Found fine")
                                }
                                "dacapo" -> {
                                    currentJumpType = "D.C."
                                    currentJumpTo = 1  // 첫 마디로 이동
                                    Log.d("MusicXmlParser", "Found D.C.")
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
                                    Log.d("MusicXmlParser", "Found D.S. to measure $currentJumpTo")
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Log.e("MusicXmlParser", "Error parsing MusicXML file", e)
            e.printStackTrace()
        }

        Log.d("MusicXmlParser", "Parsed ${measures.size} measures")
        return measures
    }

    private fun findRepeatStart(measures: List<Measure>, currentIndex: Int): Int {
        for (i in currentIndex downTo 0) {
            if (measures[i].repeatStart) {
                return i
            }
        }
        return -1
    }
} 