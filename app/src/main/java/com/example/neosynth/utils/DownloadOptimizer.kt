package com.example.neosynth.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Utilidad para optimizar las descargas según las capacidades del dispositivo.
 * 
 * Adaptado para dispositivos con recursos limitados (Galaxy A7 2018, etc.)
 */
object DownloadOptimizer {
    
    /**
     * Calcula el tamaño óptimo de batch para descargas paralelas.
     * 
     * Considera:
     * - RAM total del dispositivo
     * - Versión de Android (dispositivos más antiguos necesitan más tiempo)
     * - Memoria disponible actual
     * 
     * @param context Contexto de la aplicación
     * @return Número óptimo de descargas paralelas por batch
     */
    fun getOptimalBatchSize(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalRamMB = memInfo.totalMem / (1024 * 1024)
        val availableRamMB = memInfo.availMem / (1024 * 1024)
        val isOlderDevice = Build.VERSION.SDK_INT < Build.VERSION_CODES.R // Android < 11
        val isLowMemory = memInfo.lowMemory
        
        android.util.Log.d("DownloadOptimizer", "═══════════════════════════════════════")
        android.util.Log.d("DownloadOptimizer", "Detección de capacidades del dispositivo:")
        android.util.Log.d("DownloadOptimizer", "  - Android Version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        android.util.Log.d("DownloadOptimizer", "  - Total RAM: $totalRamMB MB")
        android.util.Log.d("DownloadOptimizer", "  - Available RAM: $availableRamMB MB")
        android.util.Log.d("DownloadOptimizer", "  - Low Memory: $isLowMemory")
        android.util.Log.d("DownloadOptimizer", "  - Device Model: ${Build.MODEL}")
        
        // Estrategia adaptativa
        val batchSize = when {
            // Caso crítico: poca RAM o memoria baja
            isLowMemory || totalRamMB < 2048 -> {
                android.util.Log.d("DownloadOptimizer", "  - Perfil: LOW_END (RAM crítica)")
                3 // Solo 3 descargas paralelas
            }
            
            // Dispositivos antiguos (Android < 11) o RAM limitada
            isOlderDevice || totalRamMB < 3072 -> {
                android.util.Log.d("DownloadOptimizer", "  - Perfil: MID_LOW (Dispositivo antiguo o RAM limitada)")
                5 // 5 descargas paralelas
            }
            
            // Rango medio: 3-4 GB RAM o Android 11-12
            totalRamMB < 4096 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                android.util.Log.d("DownloadOptimizer", "  - Perfil: MID (Gama media)")
                7 // 7 descargas paralelas
            }
            
            // Dispositivos de alta gama: 4+ GB RAM y Android 12+
            else -> {
                android.util.Log.d("DownloadOptimizer", "  - Perfil: HIGH_END (Gama alta)")
                10 // 10 descargas paralelas
            }
        }
        
        android.util.Log.d("DownloadOptimizer", "  → Batch size seleccionado: $batchSize")
        android.util.Log.d("DownloadOptimizer", "═══════════════════════════════════════")
        
        return batchSize
    }
    
    /**
     * Determina si el dispositivo debe usar configuraciones optimizadas para hardware antiguo.
     * Usado en DownloadWorker para ajustar timeouts y buffers.
     */
    fun isOlderDevice(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    }
    
    /**
     * Obtiene el tamaño de buffer óptimo para I/O de archivos.
     * 
     * @return Tamaño de buffer en bytes
     */
    fun getOptimalBufferSize(): Int {
        return if (isOlderDevice()) {
            16 * 1024 // 16 KB para dispositivos antiguos
        } else {
            8 * 1024 // 8 KB para dispositivos modernos
        }
    }
    
    /**
     * Obtiene los timeouts óptimos para OkHttp según el dispositivo.
     * 
     * @return Triple de (connectTimeout, readTimeout, writeTimeout) en segundos
     */
    fun getOptimalTimeouts(): Triple<Long, Long, Long> {
        return if (isOlderDevice()) {
            Triple(60L, 120L, 120L) // Timeouts más largos para dispositivos antiguos
        } else {
            Triple(30L, 60L, 60L) // Timeouts estándar para dispositivos modernos
        }
    }
}
