package com.example.scoreviewer

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object MidiLoader {
    fun getMidiDurationMillis(midiFile: File): Long {
        FileInputStream(midiFile).use { fis ->
            val fc = fis.channel
            val buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size())
            buffer.order(ByteOrder.BIG_ENDIAN)

            // Check header
            if (buffer.int != 0x4D546864) return 0 // "MThd"
            val headerLength = buffer.int
            val format = buffer.short.toInt()
            val numTracks = buffer.short.toInt()
            val division = buffer.short.toInt()

            Log.d("MidiLoader", "Parsed header: format=$format, tracks=$numTracks, division=$division")

            var tempo = 500000 // default tempo in microseconds per quarter note
            var maxTicks = 0L

            for (i in 0 until numTracks) {
                if (buffer.remaining() < 8) break
                if (buffer.int != 0x4D54726B) return 0 // "MTrk"
                val trackLength = buffer.int
                val trackEnd = buffer.position() + trackLength

                Log.d("MidiLoader", "Parsing track $i at buffer position ${buffer.position()} length=$trackLength")

                var ticks = 0L
                var lastStatus = -1
                while (buffer.position() < trackEnd) {
                    val delta = readVarLen(buffer)
                    ticks += delta

                    if (!buffer.hasRemaining()) {
                        Log.w("MidiLoader", "Track $i ended unexpectedly")
                        break
                    }

                    var statusByte = buffer.get().toInt() and 0xFF
                    if (statusByte < 0x80) {
                        // Running status
                        if (lastStatus == -1) {
                            Log.e("MidiLoader", "Running status used but no lastStatus defined at tick $ticks")
                            break
                        }
                        buffer.position(buffer.position() - 1) // rewind 1 byte
                        statusByte = lastStatus
                    } else {
                        lastStatus = statusByte
                    }

                    if (statusByte == 0xFF) { // Meta event
                        if (!buffer.hasRemaining()) break
                        val metaType = buffer.get().toInt() and 0xFF
                        val length = readVarLen(buffer)
                        if (metaType == 0x51 && length == 3) { // Set Tempo
                            tempo = (buffer.get().toInt() and 0xFF shl 16) or
                                    (buffer.get().toInt() and 0xFF shl 8) or
                                    (buffer.get().toInt() and 0xFF)
                            Log.d("MidiLoader", "Set tempo at tick $ticks: $tempo µs/qn")
                        } else {
                            buffer.position(buffer.position() + length)
                        }
                    } else if (statusByte in 0x80..0xEF) {
                        val paramLen = if (statusByte in 0xC0..0xDF) 1 else 2
                        buffer.position(buffer.position() + paramLen)
                    } else if (statusByte == 0xF0 || statusByte == 0xF7) {
                        val length = readVarLen(buffer)
                        buffer.position(buffer.position() + length)
                    } else {
                        Log.w("MidiLoader", "Unknown event type: $statusByte at tick $ticks")
                        break
                    }
                }

                Log.d("MidiLoader", "Track $i ticks: $ticks")
                maxTicks = maxOf(maxTicks, ticks)
                buffer.position(trackEnd)
            }

            val durationMillis = ticksToMillis(maxTicks, tempo, division)
            Log.d("MidiLoader", "Total duration: $durationMillis ms (ticks=$maxTicks, tempo=$tempo, division=$division)")
            return durationMillis
        }
    }

    private fun readVarLen(buffer: ByteBuffer): Int {
        var value = 0
        var currentByte: Int
        do {
            if (!buffer.hasRemaining()) return value
            currentByte = buffer.get().toInt() and 0xFF
            value = (value shl 7) or (currentByte and 0x7F)
        } while (currentByte and 0x80 != 0)
        return value
    }

    private fun ticksToMillis(ticks: Long, tempo: Int, division: Int): Long {
        return (ticks * tempo / division) / 1000
    }
}