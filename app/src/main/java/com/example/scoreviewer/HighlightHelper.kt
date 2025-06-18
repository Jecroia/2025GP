package com.example.scoreviewer

import android.graphics.RectF
import android.util.Log
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
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
        val page = jsonData.optJSONObject(pageNumber.toString()) ?: return null

        val topPercent = page.optDouble("topMarginPercent", 0.0)
        val bottomPercent = page.optDouble("bottomMarginPercent", 0.0)

        // lineCount 우선, 없으면 "line" 키 수
        val totalLines = page.optInt("lineCount", -1).takeIf { it > 0 }
            ?: page.keys().asSequence().count { it.startsWith("line") }

        if (lineIndex >= totalLines || totalLines == 0) return null

        // ① 실제 악보 영역 높이(마진 제외)
        val effectiveHeightPx = pageHeightPx * (1.0 - (topPercent + bottomPercent) / 100.0)
        // ② 각 라인 높이(px)
        val lineHeightPx = effectiveHeightPx / totalLines

        // ③ 시작/끝 좌표를 각각 독립적으로 계산 – 이중 합산 방지
        val topMarginPx = topPercent / 100.0 * pageHeightPx

        val startY = topMarginPx + lineIndex * lineHeightPx
        val endY = topMarginPx + (lineIndex + 1) * lineHeightPx

        Log.d(
            "HighlightHelper",
            "calculateHighlightY page=$pageNumber top=$topPercent bottom=$bottomPercent totalLines=$totalLines lineIndex=$lineIndex startY=$startY endY=$endY"
        )

        return Pair(startY.toFloat(), endY.toFloat())
    }

    fun splitLineDuration(
        startTimeMs: Int,
        endTimeMs: Int,
        lineDescription: String
    ): List<Int> {
        val ratios = parseMeasureRatios(lineDescription)
        // 추가 디버그: 라인 설명과 파싱된 비율 로그
        Log.d("HighlightDebug", "lineDesc=$lineDescription, parsedRatios=$ratios")
        val total = ratios.sum()
        val totalMs = endTimeMs - startTimeMs

        // total 이 0 또는 NaN 이면 균등 분배
        if (total <= 0 || total.isNaN()) {
            val part = (totalMs.toDouble() / ratios.size).roundToInt()
            val result = List(ratios.size) { part }
            Log.d("HighlightHelper", "splitLineDuration (uniform): ratios=$ratios totalMs=$totalMs result=$result")
            return result
        }

        val baseDurations = ratios.map { ((it / total) * totalMs).roundToInt() }.toMutableList()
        // 누적 반올림 오차 보정: 마지막 값에 차이를 더해 합계를 맞춘다
        val adjustment = totalMs - baseDurations.sum()
        if (baseDurations.isNotEmpty()) {
            baseDurations[baseDurations.lastIndex] = (baseDurations.last() + adjustment)
        }

        Log.d("HighlightHelper", "splitLineDuration: ratios=$ratios totalMs=$totalMs result=$baseDurations (adjust=$adjustment)")
        return baseDurations
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