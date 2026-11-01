package com.mansoor.djapp

import android.media.audiofx.Visualizer
import android.os.SystemClock

/**
 * Best-effort "break" detector. Spotify gives apps no audio analysis, so this listens to
 * the phone's audio output through Android's Visualizer (FFT only, nothing is recorded)
 * and reports when energy in the voice band (250-3500 Hz) drops well below the track's
 * running average, which usually means a break or a part without vocals.
 */
class LullDetector {
    private var viz: Visualizer? = null
    private var shortAvg = 0.0
    private var longAvg = 0.0
    private var frames = 0
    private var lullSince = 0L
    private var lastSignalAt = 0L

    fun start(): Boolean = try {
        val v = Visualizer(0)
        v.setEnabled(false)
        val range = Visualizer.getCaptureSizeRange()
        v.setCaptureSize(1024.coerceIn(range[0], range[1]))
        v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

            override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                if (fft != null) onFft(fft, samplingRate)
            }
        }, Visualizer.getMaxCaptureRate(), false, true)
        v.setEnabled(true)
        viz = v
        true
    } catch (e: Throwable) {
        stop()
        false
    }

    fun stop() {
        try {
            viz?.setEnabled(false)
            viz?.release()
        } catch (_: Throwable) {
        }
        viz = null
    }

    /** Call when a new track starts so averages describe the current track only. */
    fun reset() {
        frames = 0
        shortAvg = 0.0
        longAvg = 0.0
        lullSince = 0
    }

    fun hasData(): Boolean = viz != null && SystemClock.elapsedRealtime() - lastSignalAt < 2000

    fun isLull(): Boolean =
        frames > 150 && lullSince > 0 && SystemClock.elapsedRealtime() - lullSince > 800

    private fun onFft(fft: ByteArray, samplingRateMilliHz: Int) {
        val n = fft.size / 2
        if (n < 4) return
        val binHz = samplingRateMilliHz / 1000.0 / fft.size
        var mid = 0.0
        var total = 0.0
        for (k in 1 until n) {
            val re = fft[2 * k].toDouble()
            val im = fft[2 * k + 1].toDouble()
            val m = re * re + im * im
            total += m
            val f = k * binHz
            if (f in 250.0..3500.0) mid += m
        }
        if (total < 4) return // silence
        val now = SystemClock.elapsedRealtime()
        lastSignalAt = now
        frames++
        shortAvg = if (frames == 1) mid else shortAvg * 0.85 + mid * 0.15
        longAvg = if (frames < 40) longAvg + (mid - longAvg) / frames else longAvg * 0.997 + mid * 0.003
        if (shortAvg < longAvg * 0.55) {
            if (lullSince == 0L) lullSince = now
        } else {
            lullSince = 0
        }
    }
}
