package com.koceng.oren.audio

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Menganalisis PCM 16-bit mono untuk membuat profil suara.
 *
 * Pipeline:
 * 1. hilangkan DC offset
 * 2. bagi menjadi frame 40 ms, tentukan frame bersuara via RMS
 * 3. estimasi pitch dengan autocorrelation
 * 4. estimasi tempo dari jumlah serangan suara (syllable-ish pulses)
 * 5. petakan ke parameter TextToSpeech + kelas suara
 */
object VoiceProfileAnalyzer {

    private const val FRAME_MS = 40
    private const val MIN_PITCH_HZ = 70
    private const val MAX_PITCH_HZ = 320
    private const val MIN_VOICED_FRAMES = 4
    private const val MAX_FRAMES_ANALYZED = 40

    fun analyze(
        pcmData: ByteArray,
        sampleRate: Int,
        durationSeconds: Float = 0f
    ): VoiceProfile? {
        if (sampleRate < 8000 || pcmData.size < sampleRate) return null

        val samples = ShortArray(pcmData.size / 2) { i ->
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt() and 0xFF
            (low or (high shl 8)).toShort()
        }
        if (samples.size < sampleRate / 2) return null

        // Buang DC offset supaya autocorrelation tidak bias.
        var mean = 0.0
        for (sample in samples) mean += sample.toDouble()
        mean /= samples.size
        for (i in samples.indices) {
            samples[i] = (samples[i].toDouble() - mean).toInt().toShort()
        }

        val frameSize = (sampleRate * FRAME_MS / 1000).coerceAtLeast(64)
        val frameCount = samples.size / frameSize
        if (frameCount < 6) return null

        val frameRms = DoubleArray(frameCount)
        for (frame in 0 until frameCount) {
            val start = frame * frameSize
            var sum = 0.0
            for (i in start until start + frameSize) {
                val value = samples[i].toDouble()
                sum += value * value
            }
            frameRms[frame] = sqrt(sum / frameSize)
        }

        val sortedRms = frameRms.copyOf().sorted()
        val noiseFloor = sortedRms[sortedRms.size / 4]
        val peak = sortedRms[sortedRms.lastIndex]
        val threshold = max(250.0, noiseFloor + (peak - noiseFloor) * 0.35)

        val voicedThreshold = threshold * 0.98
        val voicedFrames = frameRms.indices.filter { frameRms[it] >= voicedThreshold }
        if (voicedFrames.size < MIN_VOICED_FRAMES) return null

        val minLag = max(2, sampleRate / MAX_PITCH_HZ)
        val maxLag = minOf(sampleRate / MIN_PITCH_HZ, frameSize / 2)
        if (maxLag <= minLag) return null

        val pitchCandidates = mutableListOf<Float>()
        for (frame in voicedFrames.take(MAX_FRAMES_ANALYZED)) {
            val pitch = estimatePitch(
                samples = samples,
                frameStart = frame * frameSize,
                frameSize = frameSize,
                sampleRate = sampleRate,
                minLag = minLag,
                maxLag = maxLag
            ) ?: continue
            if (pitch in MIN_PITCH_HZ.toFloat()..MAX_PITCH_HZ.toFloat()) {
                pitchCandidates.add(pitch)
            }
        }
        if (pitchCandidates.isEmpty()) return null

        val pitchHz = pitchCandidates.sorted()[pitchCandidates.size / 2]

        var pulses = 0
        for (i in 1 until frameCount) {
            if (frameRms[i] >= voicedThreshold && frameRms[i - 1] < voicedThreshold) pulses++
        }
        val duration = if (durationSeconds > 0.25f) durationSeconds else samples.size.toFloat() / sampleRate
        val speakingRate = (pulses.toFloat() / duration).coerceIn(1.2f, 9f)

        val ttsPitch = (0.8f + ((pitchHz - 90f) / 170f) * 0.6f).coerceIn(0.75f, 1.45f)
        val ttsSpeed = (speakingRate / 4.2f).coerceIn(0.8f, 1.3f)

        val voiceClass = when {
            pitchHz < 150f -> VoiceClass.LOW
            pitchHz < 210f -> VoiceClass.MEDIUM
            else -> VoiceClass.HIGH
        }

        return VoiceProfile(
            pitchHz = pitchHz,
            speakingRate = speakingRate,
            ttsPitch = ttsPitch,
            ttsSpeed = ttsSpeed,
            voiceClass = voiceClass,
            sampleCount = samples.size
        )
    }

    /** Autocorrelation ringan dengan stride 2 untuk hemat CPU di HP. */
    private fun estimatePitch(
        samples: ShortArray,
        frameStart: Int,
        frameSize: Int,
        sampleRate: Int,
        minLag: Int,
        maxLag: Int
    ): Float? {
        val stride = 2
        var energy = 0.0
        var count = 0
        var i = frameStart
        while (i < frameStart + frameSize) {
            val value = samples[i].toDouble()
            energy += value * value
            count++
            i += stride
        }
        if (energy <= 1e-6 || count == 0) return null

        val referenceEnergy = energy / count
        val scores = DoubleArray(maxLag - minLag + 1)

        for (idx in scores.indices) {
            val lag = minLag + idx
            var correlation = 0.0
            var compared = 0
            var pos = frameStart
            while (pos + lag < frameStart + frameSize) {
                correlation += samples[pos].toDouble() * samples[pos + lag].toDouble()
                compared++
                pos += stride
            }
            scores[idx] = if (compared == 0) Double.NEGATIVE_INFINITY
            else correlation / compared / referenceEnergy
        }

        val bestScore = scores.maxOrNull() ?: return null
        if (bestScore < 0.25) return null

        // Autocorrelation sering punya puncak di kelipatan periode (oktaf lebih rendah).
        // Ambil puncak lokal kuat PERTAMA, lalu perhalus dengan interpolasi parabola.
        var chosenIndex = -1
        for (idx in 1 until scores.lastIndex) {
            val isPeak = scores[idx] >= scores[idx - 1] && scores[idx] > scores[idx + 1]
            if (isPeak && scores[idx] >= bestScore * 0.75) {
                chosenIndex = idx
                break
            }
        }
        if (chosenIndex <= 0) return null

        val a = scores[chosenIndex - 1]
        val b = scores[chosenIndex]
        val c = scores[chosenIndex + 1]
        val denominator = a - 2 * b + c
        val offset = if (denominator != 0.0) (0.5 * (a - c) / denominator).coerceIn(-1.0, 1.0) else 0.0
        val refinedLag = (minLag + chosenIndex).toFloat() + offset.toFloat()
        return sampleRate.toFloat() / refinedLag
    }
}
