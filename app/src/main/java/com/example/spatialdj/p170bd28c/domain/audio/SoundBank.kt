package com.example.spatialdj.p170bd28c.domain.audio

import com.example.spatialdj.p170bd28c.domain.model.Lane
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Interleaved stereo. Production loads original offline-generated PCM once, before audio starts.
 * Deterministic synthesis remains available for rate-scaled JVM sequencer tests. */
class SoundBank(val sampleRate: Int = 48000, loader: ((Int,Int)->FloatArray)? = null) {
    val sounds: Array<Array<FloatArray>> = Array(5) { lane ->
        Array(Lane.entries[lane].pads.size) { pad ->
            (loader?.invoke(lane,pad) ?: make(lane,pad).let { mono ->
                FloatArray(mono.size*2) { mono[it/2] }
            }).also { require(it.isNotEmpty() && it.size%2==0 && it.all(Float::isFinite)) }
        }
    }
    val click = FloatArray(maxOf(1,(sampleRate * .035).toInt())*2) { n ->
        val t = (n/2).toDouble() / sampleRate
        (sin(2 * PI * 1600 * t) * exp(-t * 130) * .2).toFloat()
    }
    private fun make(lane: Int, pad: Int): FloatArray {
        val duration = when (lane) { 0 -> if (pad == 2) .36 else .23; 1 -> .25; 2 -> .42; 3 -> .5; else -> 1.2 }
        var random = 1287L + lane * 31 + pad
        var lowNoise = 0.0
        var phase = 0.0
        val midi = when (lane) {
            2 -> intArrayOf(33, 36, 38, 40, 29, 31)[pad]
            3 -> intArrayOf(57, 60, 62, 64, 67, 69, 72, 76)[pad]
            else -> 57
        }
        val frequency = 440 * 2.0.pow((midi - 69) / 12.0)
        val chords = arrayOf(intArrayOf(57,60,64), intArrayOf(53,57,60),
            intArrayOf(48,52,55), intArrayOf(55,59,62))
        return FloatArray((duration * sampleRate).toInt()) { n ->
            val t = n.toDouble() / sampleRate
            random = (random * 1664525 + 1013904223) and 0xffffffffL
            val noise = random.toDouble() / 2147483648.0 - 1
            lowNoise += .16 * (noise - lowNoise)
            val attack = min(1.0, t / .004)
            val end = min(1.0, (duration - t) / .045)
            val signal = when (lane) {
                0 -> when (pad) {
                    0 -> { phase += 2 * PI * (48 + 120 * exp(-t * 45)) / sampleRate
                        sin(phase) * exp(-t * 17) * .9 + noise * exp(-t * 300) * .1 }
                    1,2 -> (noise - lowNoise) * exp(-t * if (pad == 1) 40 else 12) * .4
                    else -> sin(2 * PI * (105 * t + .8 * (1 - exp(-t * 20)))) * exp(-t * 16) * .65
                }
                1 -> {
                    val pulse = if (pad == 1) 1.0 else
                        if (t < .008 || t in .016.. .024 || t in .032.. .040 || t > .048) 1.0 else .15
                    (noise - lowNoise) * exp(-t * 19) * pulse * when(pad) {2->.35;3->.85;else->.65} +
                        if (pad == 1) sin(2 * PI * 170 * t) * exp(-t * 30) * .25 else 0.0
                }
                2 -> (sin(2 * PI * frequency * t) + .24 * sin(4 * PI * frequency * t)) *
                    exp(-t * 5) * .6
                3 -> (sin(2 * PI * frequency * t) + .35 * sin(4 * PI * frequency * t) +
                    .12 * sin(6 * PI * frequency * t)) * exp(-t * 6) * .42
                else -> chords[pad].sumOf { note ->
                    val f = 440 * 2.0.pow((note - 69) / 12.0)
                    sin(2 * PI * f * t) + .18 * sin(4 * PI * f * t)
                } * exp(-t * 2.5) * .17
            }
            (signal * attack * end).toFloat()
        }
    }
    companion object {
        fun readStereoWav(input: InputStream, sampleRate: Int = 48000): FloatArray {
            val bytes=input.use { it.readBytes() }
            require(bytes.size in 44..2_000_000)
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(buffer.int==0x46464952 && buffer.int==bytes.size-8 && buffer.int==0x45564157)
            var formatOk=false
            while(buffer.remaining()>=8) {
                val tag=buffer.int; val size=buffer.int
                require(size>=0 && size<=buffer.remaining())
                val end=buffer.position()+size
                if(tag==0x20746d66) {
                    require(size>=16)
                    val format=buffer.short.toInt(); val channels=buffer.short.toInt()
                    val rate=buffer.int; val byteRate=buffer.int; val align=buffer.short.toInt(); val bits=buffer.short.toInt()
                    require(format==1 && channels==2 && rate==sampleRate && byteRate==rate*4 && align==4 && bits==16)
                    formatOk=true
                } else if(tag==0x61746164) {
                    require(formatOk && size>0 && size%4==0)
                    return FloatArray(size/2) { buffer.short / 32768f }
                }
                buffer.position(end+(size%2))
            }
            error("Stereo PCM data missing")
        }
    }
}
