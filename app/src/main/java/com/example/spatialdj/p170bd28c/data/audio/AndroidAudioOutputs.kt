package com.example.spatialdj.p170bd28c.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Looper
import android.util.Log
import com.example.spatialdj.p170bd28c.domain.model.*
import com.pico.spatial.audio.SpatialAudioTrackExtension
import com.pico.spatial.core.ecs.Entity
import kotlin.math.max

/** SDK binding and release are deliberately owned by the Stage's Main lifetime. */
class AndroidAudioOutputFactory(private val speakers: Pair<Entity, Entity>? = null) : AudioOutputFactory {
    override fun create(): AudioOutput {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (speakers != null) {
            val tracks = ArrayList<AudioTrack>(2)
            val extensions = ArrayList<SpatialAudioTrackExtension>(2)
            try {
                for (speaker in listOf(speakers.first, speakers.second)) {
                    check(speaker.valid) { "空间音箱已被释放" }
                    val builder = builder(AudioFormat.CHANNEL_OUT_MONO)
                    val extension = SpatialAudioTrackExtension()
                    extensions.add(extension)
                    extension.spatialAudioTrackExtensionConfig(
                        SpatialAudioTrackExtension.SpatialAudioMode.OBJECT, false, builder)
                    extension.attachToEntityWithBuilder(speaker, builder)
                    val audio = builder.build()
                    tracks.add(audio)
                    check(audio.state == AudioTrack.STATE_INITIALIZED)
                    check(extension.enableSpatialAudio(true, audio)) { "设备未启用位置音频" }
                }
                Log.i("SpatialDJ", "SpatialAudioReady sources=2 mode=OBJECT channels=L/R")
                return Output(tracks, extensions, "左右空间音箱")
            } catch (error: Exception) {
                tracks.forEach { runCatching { it.stop() } }
                extensions.forEach { runCatching { it.release() } }
                tracks.forEach { it.release() }
                Log.w("SpatialDJ", "SpatialAudioFallback: ${error.message}")
            }
        }
        val track = builder(AudioFormat.CHANNEL_OUT_STEREO).build()
        check(track.state == AudioTrack.STATE_INITIALIZED) { "立体声输出初始化失败" }
        return Output(listOf(track), emptyList(), "立体声监听")
    }

    private fun builder(mask: Int): AudioTrack.Builder {
        val minimum = AudioTrack.getMinBufferSize(48000, mask, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "设备不支持 48 kHz 输出" }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(mask)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(max(minimum, if (mask == AudioFormat.CHANNEL_OUT_MONO) 768 else 1536))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
    }

    private class Output(
        val tracks: List<AudioTrack>,
        val extensions: List<SpatialAudioTrackExtension>,
        override val description: String,
    ) : AudioOutput {
        private val left = ShortArray(192)
        private val right = ShortArray(192)
        @Volatile private var stopped = false
        override fun start() {
            tracks.forEach { track ->
                // B3110's object spatializer consumes 512-frame periods. Three periods keep
                // both channels supplied while cutting its default 3844-frame queue.
                val actual=track.setBufferSizeInFrames(
                    if(tracks.size==2 || track.performanceMode!=AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) 1536 else 384)
                check(actual>0) { "音频缓冲配置失败 $actual" }
                Log.i("SpatialLoop","AudioBuffer frames=${track.bufferSizeInFrames} capacity=${track.bufferCapacityInFrames} " +
                    "mode=${track.performanceMode} rate=${track.sampleRate}")
            }
            // Prime both clocks with the same silent batch before starting them on Main.
            if (tracks.size == 2) {
                repeat(3) {
                    tracks[0].write(left, 0, 192, AudioTrack.WRITE_NON_BLOCKING)
                    tracks[1].write(right, 0, 192, AudioTrack.WRITE_NON_BLOCKING)
                }
            }
            tracks.forEach { it.play() }
        }
        override fun write(stereo: ShortArray): Boolean {
            if (tracks.size == 1) return writeTrack(tracks[0], stereo)
            require(stereo.size == 384)
            for (frame in 0 until 192) {
                left[frame] = stereo[frame * 2]; right[frame] = stereo[frame * 2 + 1]
            }
            return writeTrack(tracks[0], left) && writeTrack(tracks[1], right)
        }
        private fun writeTrack(track: AudioTrack, buffer: ShortArray): Boolean {
            var at = 0
            while (!stopped && at < buffer.size) {
                val n = track.write(buffer, at, buffer.size - at, AudioTrack.WRITE_BLOCKING)
                if (n < 0) { if (stopped) return false else error("PCM 输出错误 $n") }
                if (n == 0) Thread.yield() else at += n
            }
            return !stopped
        }
        override fun stop() {
            stopped = true
            tracks.forEach { runCatching { it.pause(); it.flush(); it.stop() } }
        }
        override fun close() {
            check(Looper.myLooper() == Looper.getMainLooper())
            extensions.forEach { runCatching { it.release() } }
            tracks.forEach { it.release() }
        }
    }
}
