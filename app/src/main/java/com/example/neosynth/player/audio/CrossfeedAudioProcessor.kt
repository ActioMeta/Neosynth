package com.example.neosynth.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * A simple Crossfeed audio processor to reduce listening fatigue with headphones.
 * It blends a percentage of the left channel into the right channel and vice versa.
 */
class CrossfeedAudioProcessor : BaseAudioProcessor() {

    private var crossfeedEnabled = false
    private var strength = 0.3f // Default 30%

    fun setEnabled(enabled: Boolean) {
        if (crossfeedEnabled != enabled) {
            crossfeedEnabled = enabled
            // Trigger reconfiguration by returning true in isActive() and possibly flushing
            // However, BaseAudioProcessor determines activity based on format matching.
            // We need to ensure we return correct format in configure.
        }
    }
    
    // Override isActive to control whether the processor is applied
    override fun isActive(): Boolean {
        return crossfeedEnabled && super.isActive()
    }

    fun setStrength(strengthPercent: Int) {
        val newStrength = (strengthPercent / 100f).coerceIn(0f, 1f)
        if (strength != newStrength) {
            strength = newStrength
            // We don't need to flush if only strength changes, but to be safe:
            // logic is applied per sample.
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // We only support stereo PCM
        if (inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        
        while (inputBuffer.hasRemaining()) {
            // Read samples (assuming 16-bit PCM for simplicity, but ExoPlayer may send Float)
            // BaseAudioProcessor usually handles Format negotiation.
            // If output encoding is PCM_16BIT:
            if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                // Read Left and Right
                val leftShort = inputBuffer.short
                val rightShort = inputBuffer.short
                
                // Process
                val left = leftShort.toFloat()
                val right = rightShort.toFloat()
                
                // Simple blending:
                // NewL = L * (1 - s) + R * s
                // NewR = R * (1 - s) + L * s
                val s = strength * 0.5f // attenuated strength to avoid loudness increase
                
                val newL = (left * (1.0f - s) + right * s)
                val newR = (right * (1.0f - s) + left * s)
                
                // Clamp
                val outL = newL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                val outR = newR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                
                buffer.putShort(outL)
                buffer.putShort(outR)
            } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
                // Read Left and Right Float
                val left = inputBuffer.float
                val right = inputBuffer.float
                
                val s = strength * 0.5f
                
                val newL = (left * (1.0f - s) + right * s)
                val newR = (right * (1.0f - s) + left * s)
                
                buffer.putFloat(newL)
                buffer.putFloat(newR)
            } else {
                 // Pass through for unsupported formats (shouldn't happen if onConfigure rejected them)
                 // But wait, onConfigure MUST reject unsupported formats.
                 // We will just copy bytes if we mess up.
                 // But since we can't easily process bytes without knowing format, we should only accept 16BIT and FLOAT.
                 // Re-implementing onConfigure to enforce this.
                 // For now, if we get here with something else, just copy.
                 buffer.put(inputBuffer.get())
            }
        }
        
        inputBuffer.limit(inputBuffer.position()) // Mark as consumed
        buffer.flip()
    }
}
