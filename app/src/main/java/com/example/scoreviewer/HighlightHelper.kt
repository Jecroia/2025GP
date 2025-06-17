package com.example.scoreviewer

import android.graphics.RectF
import android.util.Log
import org.json.JSONObject
import kotlin.math.roundToInt

object HighlightHelper {

    fun parseMeasureRatios(desc: String): List<Double> {
        val results = mutableListOf<Double>()

        desc.split("+").forEach { rawPart ->
            val trimmed = rawPart.trim().replace(" ", "")
            if (trimmed.isEmpty()) return@forEach

            when {
                // N마디  → N 개의 1.0 추가 (각 마디)
                trimmed.endsWith("마디") -> {
                    val numStr = trimmed.removeSuffix("마디")
                    val count = numStr.toIntOrNull() ?: 1
                    repeat(count) { results.add(1.0) }
                }

                // 박자 단위 (예: 3박자, 3+2/4박자)
                trimmed.endsWith("박자") -> {
                    val expr = trimmed.removeSuffix("박자")
                    val beatSum = expr.split("+").sumOf { sub ->
                        if ("/" in sub) {
                            val (a, b) = sub.split("/").map { it.toDouble() }
                            a / b
                        } else sub.toDouble()
                    }
                    // 4박자를 한 마디로 가정 → beatSum/4 만큼의 마디 비율
                    results.add(beatSum / 4.0)
                }

                // 분수 표현만 온 경우 (예: 1/2)
                "/" in trimmed -> {
                    val (a, b) = trimmed.split("/").map { it.toDouble() }
                    results.add(a / b)
                }

                else -> {
                    // 숫자만 온 경우 → 그대로 비율로
                    trimmed.toDoubleOrNull()?.let { results.add(it) }
                }
            }
        }

        return results
    }

    fun calculateHighlightY(
        pageHeightPx: Int,
        pageNumber: Int,
        lineIndex: Int,
        jsonData: JSONObject
    ): Pair<Float, Float>? {
        Log.i("HighlightHelper", "🔍 Entered calculateHighlightY - page=$pageNumber, line=$lineIndex")
        val page = jsonData.optJSONObject(pageNumber.toString()) ?: return null
        val topPercent = page.optDouble("topMarginPercent", 0.0)
        val bottomPercent = page.optDouble("bottomMarginPercent", 0.0)

        // 총 줄 수 계산: lineCount 키가 있으면 우선 사용, 없으면 "line" 키 개수로 계산
        val lineCountFromJson = page.optInt("lineCount", -1)

        val lineKeys = page.keys().asSequence()
            .filter { it.startsWith("line") }
            .sortedBy { it.removePrefix("line ").toIntOrNull() ?: Int.MAX_VALUE }
            .toList()
        val totalLines = if (lineCountFromJson > 0) lineCountFromJson else lineKeys.size

        Log.i("HighlightHelper",
            "page=$pageNumber top=$topPercent bottom=$bottomPercent lines=$totalLines")

        if (lineIndex >= totalLines || totalLines == 0) return null
        Log.d("HighlightHelper", "Page $pageNumber: top=$topPercent%, bottom=$bottomPercent%, totalLines=$totalLines, lineIndex=$lineIndex")
        val effectiveHeight = 1.0 - (topPercent + bottomPercent) / 100.0
        val lineHeight = effectiveHeight / totalLines
        val startY = ((topPercent / 100.0) + lineHeight * lineIndex) * pageHeightPx
        val endY = startY + (lineHeight * pageHeightPx)
        return Pair(startY.toFloat(), endY.toFloat())
    }

    fun splitLineDuration(
        startTimeMs: Int,
        endTimeMs: Int,
        lineDescription: String
    ): List<Int> {
        val ratios = parseMeasureRatios(lineDescription)
        val total = ratios.sum()
        val totalMs = endTimeMs - startTimeMs

        // total 이 0 또는 NaN 이면 균등 분배
        if (total <= 0 || total.isNaN()) {
            val part = (totalMs.toDouble() / ratios.size).roundToInt()
            val result = List(ratios.size) { part }
            Log.d("HighlightHelper", "splitLineDuration (uniform): ratios=$ratios totalMs=$totalMs result=$result")
            return result
        }

        val result = ratios.map { ((it / total) * totalMs).roundToInt() }
        Log.d("HighlightHelper", "splitLineDuration: ratios=$ratios totalMs=$totalMs result=$result")
        return result
    }

    fun getHighlightRect(
        pageWidthPx: Int,
        pageHeightPx: Int,
        pageNumber: Int,
        lineIndex: Int,
        jsonData: JSONObject,
        left: Float = 0f,
        right: Float = pageWidthPx.toFloat()
    ): RectF? {
        val (top, bottom) = calculateHighlightY(pageHeightPx, pageNumber, lineIndex, jsonData) ?: return null
        return RectF(left, top, right, bottom)
    }

    /**
     * 특정 마디 번호에 해당하는 줄 index를 찾아 해당 마디의 하이라이트 사각형과 시간 반환
     */
    fun getMeasureHighlight(
        measureNumber: Int,
        lines: List<MusicXmlParser.Line>,
        pageWidthPx: Int,
        pageHeightPx: Int,
        jsonData: JSONObject
    ): Pair<RectF?, Int>? {
        Log.i("HighlightHelper", "📌 getMeasureHighlight called for measure=$measureNumber")
        for (line in lines) {
            val indexInLine = line.measureNumbers.indexOf(measureNumber)
            if (indexInLine >= 0) {
                Log.i("HighlightHelper", "✅ measure $measureNumber found in page=${line.pageNumber}, line=${line.lineNumber}")
                val lineDesc = jsonData.optJSONObject(line.pageNumber.toString())
                    ?.optString("line ${line.lineNumber + 1}") ?: return null

                val durations = splitLineDuration(line.startTimeMs, line.endTimeMs, lineDesc)
                val highlightTime = if (indexInLine < durations.size) durations[indexInLine] else 0
                val rect = getHighlightRect(pageWidthPx, pageHeightPx, line.pageNumber, line.lineNumber, jsonData)
                return Pair(rect, highlightTime)
            }
        }
        Log.w("HighlightHelper", "⚠️ measure $measureNumber not found in any line")
        return null
    }
} 