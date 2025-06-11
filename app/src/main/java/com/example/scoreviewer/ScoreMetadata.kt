package com.example.scoreviewer

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileReader

data class LineInfo(
    val measureCount: Int,
    val hasPartialMeasure: Boolean = false,
    val partialMeasureNumerator: Int = 0,
    val partialMeasureDenominator: Int = 0
)

data class PageInfo(
    val lines: Map<String, LineInfo>
)

class ScoreMetadata(private val context: Context) {
    private var bpm: Float = 120f
    private var pages: Map<Int, PageInfo> = emptyMap()
    private var totalMeasures: Int = 0
    private var measureToPageMap: Map<Int, Int> = emptyMap()
    
    fun loadMetadata(jsonFile: File) {
        try {
            val jsonString = FileReader(jsonFile).use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // BPM 로드
            bpm = jsonObject.getDouble("bpm").toFloat()
            
            // 페이지 정보 로드
            val pagesJson = jsonObject.getJSONObject("pages")
            val pagesMap = mutableMapOf<Int, PageInfo>()
            var currentMeasure = 1
            
            pagesJson.keys().forEach { pageKey ->
                val pageNumber = pageKey.toInt()
                val pageJson = pagesJson.getJSONObject(pageKey)
                val linesMap = mutableMapOf<String, LineInfo>()
                
                pageJson.keys().forEach { lineKey ->
                    val measureText = pageJson.getString(lineKey)
                    val lineInfo = parseMeasureText(measureText)
                    linesMap[lineKey] = lineInfo
                    
                    // 마디 수 누적
                    currentMeasure += lineInfo.measureCount
                }
                
                pagesMap[pageNumber] = PageInfo(linesMap)
            }
            
            pages = pagesMap
            
            // 마디-페이지 매핑 생성
            measureToPageMap = createMeasureToPageMap()
            totalMeasures = measureToPageMap.keys.maxOrNull() ?: 0
            
        } catch (e: Exception) {
            e.printStackTrace()
            pages = emptyMap()
            measureToPageMap = emptyMap()
            totalMeasures = 0
        }
    }
    
    private fun parseMeasureText(text: String): LineInfo {
        return when {
            text.contains("+") -> {
                // "1마디 + 3+2/4박자 1마디" 같은 형식 처리
                val parts = text.split("+")
                val fullMeasures = parts[0].trim().replace("마디", "").toInt()
                val partialPart = parts[1].trim()
                val (numerator, denominator) = parsePartialMeasure(partialPart)
                LineInfo(fullMeasures, true, numerator, denominator)
            }
            else -> {
                // "4마디" 같은 형식 처리
                val measureCount = text.replace("마디", "").trim().toInt()
                LineInfo(measureCount)
            }
        }
    }
    
    private fun parsePartialMeasure(text: String): Pair<Int, Int> {
        // "3+2/4박자" 같은 형식에서 분자와 분모 추출
        val parts = text.split("/")
        val numerator = parts[0].replace("박자", "").trim().toInt()
        val denominator = parts[1].replace("박자", "").trim().toInt()
        return Pair(numerator, denominator)
    }
    
    private fun createMeasureToPageMap(): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        var currentMeasure = 1
        
        pages.entries.sortedBy { it.key }.forEach { (pageNumber, pageInfo) ->
            pageInfo.lines.values.forEach { lineInfo ->
                repeat(lineInfo.measureCount) {
                    map[currentMeasure] = pageNumber
                    currentMeasure++
                }
            }
        }
        
        return map
    }
    
    fun getBpm(): Float = bpm
    
    fun getPageForMeasure(measureNumber: Int): Int? {
        return measureToPageMap[measureNumber]
    }
    
    fun getTotalMeasures(): Int = totalMeasures
    
    fun getLineInfo(pageNumber: Int, lineNumber: String): LineInfo? {
        return pages[pageNumber]?.lines?.get(lineNumber)
    }
    
    fun getMeasuresForPage(pageNumber: Int): Int {
        return pages[pageNumber]?.lines?.values?.sumOf { it.measureCount } ?: 0
    }
} 