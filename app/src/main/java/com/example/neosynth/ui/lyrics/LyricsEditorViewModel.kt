package com.example.neosynth.ui.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neosynth.data.preferences.SettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class LyricsEditorViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    private val _rawLyrics = MutableStateFlow("")
    val rawLyrics: StateFlow<String> = _rawLyrics
    
    // Lista de líneas para la UI de sincronización interactiva
    private val _parsedLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val parsedLines: StateFlow<List<LyricLine>> = _parsedLines

    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing
    
    private val _publishStatus = MutableStateFlow<String?>(null)
    val publishStatus: StateFlow<String?> = _publishStatus

    val geminiApiKey = settingsPreferences.appSettings
    
    fun updateRawLyrics(newLyrics: String) {
        _rawLyrics.value = newLyrics
        parseTextToLines(newLyrics)
    }
    
    fun importFromFile(contents: String) {
        updateRawLyrics(contents)
    }

    private fun parseTextToLines(text: String) {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val parsed = lines.map { line ->
            // Si la línea ya tiene un timestamp [mm:ss.xx], lo extraemos, sino lo dejamos en nulo
            val timestampRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]""")
            val match = timestampRegex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val millisString = match.groupValues[3]
                // Ajustar si son 2 o 3 dígitos
                val mil = if (millisString.length == 2) millisString.toLong() * 10 else millisString.toLong()
                val timeMs = min * 60000 + sec * 1000 + mil
                
                val textOnly = line.replace(timestampRegex, "").trim()
                LyricLine(textOnly, timeMs)
            } else {
                LyricLine(line, null)
            }
        }
        _parsedLines.value = parsed
    }
    
    fun updateLineTime(index: Int, timeMs: Long) {
        val currentLines = _parsedLines.value.toMutableList()
        if (index in currentLines.indices) {
            currentLines[index] = currentLines[index].copy(timeMs = timeMs)
            _parsedLines.value = currentLines
            reconstructRawLyrics(currentLines)
        }
    }
    
    fun updateLine(index: Int, text: String, timeMs: Long?) {
        val currentLines = _parsedLines.value.toMutableList()
        if (index in currentLines.indices) {
            currentLines[index] = currentLines[index].copy(text = text, timeMs = timeMs)
            _parsedLines.value = currentLines
            reconstructRawLyrics(currentLines)
        }
    }
    
    fun removeLine(index: Int) {
        val currentLines = _parsedLines.value.toMutableList()
        if (index in currentLines.indices) {
            currentLines.removeAt(index)
            _parsedLines.value = currentLines
            reconstructRawLyrics(currentLines)
        }
    }
    
    fun clearLineTime(index: Int) {
         val currentLines = _parsedLines.value.toMutableList()
         if (index in currentLines.indices) {
             currentLines[index] = currentLines[index].copy(timeMs = null)
             _parsedLines.value = currentLines
             reconstructRawLyrics(currentLines)
         }
    }

    private fun reconstructRawLyrics(lines: List<LyricLine>) {
        val newRaw = lines.joinToString("\n") { line ->
            if (line.timeMs != null) {
                val min = line.timeMs / 60000
                val sec = (line.timeMs % 60000) / 1000
                val mil = (line.timeMs % 1000) / 10 // formato .xx
                String.format("[%02d:%02d.%02d] %s", min, sec, mil, line.text)
            } else {
                line.text
            }
        }
        _rawLyrics.value = newRaw
    }
    
    fun generateWithGemini(track: String, artist: String) {
        viewModelScope.launch {
            val apiKey = geminiApiKey.first().geminiApiKey
            if (apiKey.isBlank()) {
                _publishStatus.value = "Configura la API Key de Gemini en Ajustes."
                return@launch
            }
            
            _publishStatus.value = "Generando letras con Gemini..."
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
                    
                    // Log para debugging - muestra la key parcialmente enmascarada
                    val maskedKey = if (apiKey.length > 8) apiKey.take(4) + "****" + apiKey.takeLast(4) else "KEY_DEMASIADO_CORTA"
                    android.util.Log.d("GeminiAPI", "Haciendo petición a: $url")
                    android.util.Log.d("GeminiAPI", "API Key usada: $maskedKey (longitud: ${apiKey.length})")
                    
                    val prompt = """
                        Please provide ONLY the raw lyrics for the song "$track" by "$artist".
                        Do not include timestamps, just the plain text lyrics line by line.
                        Do not include any introductions, apologies, or explanations. 
                        Just the lyrics.
                    """.trimIndent()
                    
                    val jsonBody = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", prompt)
                                    })
                                })
                            })
                        })
                    }
                    
                    val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            android.util.Log.e("GeminiAPI", "Error ${response.code}: $errorBody")
                            when (response.code) {
                                429 -> throw Exception("Límite de peticiones alcanzado. Espera un minuto e intenta de nuevo.")
                                401, 403 -> throw Exception("API Key inválida. Verifica tu API Key en Ajustes.")
                                else -> throw Exception("Error ${response.code}: ${response.message}")
                            }
                        }
                        
                        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                        val responseJson = JSONObject(responseBody)
                        val candidates = responseJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                parts.getJSONObject(0).optString("text", "")
                            } else {
                                throw Exception("No text parts found in Gemini response.")
                            }
                        } else {
                            throw Exception("No candidates found in Gemini response.")
                        }
                    }
                }
                
                updateRawLyrics(result.trim())
                _publishStatus.value = "Letras generadas exitosamente."
            } catch (e: Exception) {
                e.printStackTrace()
                _publishStatus.value = "Error al generar letras: ${e.message}"
            }
        }
    }
    
    fun publishLyrics(track: String, artist: String, album: String, duration: Int) {
        viewModelScope.launch {
            _isPublishing.value = true
            _publishStatus.value = "Solicitando desafío a LRCLib..."

            try {
                val client = OkHttpClient()
                val lrclibBase = "https://lrclib.net/api"

                // 1. Request challenge
                val challengeJson = withContext(Dispatchers.IO) {
                    val challengeBody = "".toRequestBody("text/plain".toMediaType())
                    val req = Request.Builder()
                        .url("$lrclibBase/request-challenge")
                        .addHeader("User-Agent", "NeoSynth Android")
                        .post(challengeBody)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val responseBody = resp.body?.string()
                        if (!resp.isSuccessful) {
                            throw Exception("Challenge error: HTTP ${resp.code} ${resp.message}. ${responseBody.orEmpty()}")
                        }
                        JSONObject(responseBody ?: throw Exception("Empty challenge body"))
                    }
                }
                val prefix = challengeJson.getString("prefix")
                val targetHex = challengeJson.getString("target")

                android.util.Log.d("LRCLib", "PoW prefix=$prefix  target=$targetHex")
                _publishStatus.value = "Calculando Proof of Work..."

                // 2. Compute nonce (in Default dispatcher to avoid blocking IO threadpool)
                val nonce = withContext(Dispatchers.Default) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    val targetInt = BigInteger(targetHex, 16)
                    var n = 0L
                    while (true) {
                        val input = "$prefix$n"
                        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
                        val hashInt = BigInteger(1, hashBytes)
                        if (hashInt <= targetInt) break
                        n++
                        if (n % 100_000L == 0L) kotlinx.coroutines.yield()
                    }
                    n
                }
                android.util.Log.d("LRCLib", "PoW solved: nonce=$nonce")
                _publishStatus.value = "Publicando en LRCLib..."

                // 3. Build plain lyrics (strip timestamps) and synced lyrics (the LRC raw)
                val currentLines = _parsedLines.value
                val syncedLyrics = _rawLyrics.value.trim()
                val plainLyrics = currentLines.joinToString("\n") { it.text }

                val bodyJson = JSONObject().apply {
                    put("trackName", track)
                    put("artistName", artist)
                    put("albumName", album)
                    put("duration", duration)
                    put("plainLyrics", plainLyrics)
                    put("syncedLyrics", syncedLyrics)
                }

                val responseCode = withContext(Dispatchers.IO) {
                    val body = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val publishToken = "$prefix:$nonce"
                    val req = Request.Builder()
                        .url("$lrclibBase/publish")
                        .addHeader("User-Agent", "NeoSynth Android")
                        .addHeader("X-Publish-Token", publishToken)
                        .post(body)
                        .build()
                    client.newCall(req).execute().use { resp ->
                        android.util.Log.d("LRCLib", "Publish response: ${resp.code} ${resp.body?.string()}")
                        resp.code
                    }
                }

                _publishStatus.value = when (responseCode) {
                    201 -> "¡Letras publicadas correctamente en LRCLib!"
                    400 -> "Error: token/challenge inválido o datos incorrectos."
                    405 -> "Error: método HTTP no permitido por LRCLib."
                    409 -> "Ya existen letras para esta canción en LRCLib."
                    else -> "Error al publicar (HTTP $responseCode)."
                }

            } catch (e: Exception) {
                android.util.Log.e("LRCLib", "Publish failed", e)
                _publishStatus.value = "Error al publicar: ${e.message}"
            } finally {
                _isPublishing.value = false
            }
        }
    }
    
    fun clearStatus() {
        _publishStatus.value = null
    }
}

data class LyricLine(
    val text: String,
    val timeMs: Long? = null
)
