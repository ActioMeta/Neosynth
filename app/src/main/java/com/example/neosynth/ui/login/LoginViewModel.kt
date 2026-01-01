package com.example.neosynth.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.neosynth.data.local.ServerDao
import com.example.neosynth.data.local.entities.ServerEntity
import com.example.neosynth.data.remote.NavidromeApiService
import com.example.neosynth.data.remote.DynamicUrlInterceptor
import com.example.neosynth.utils.AuthUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Loading : ConnectionStatus()
    object Success : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val api: NavidromeApiService,
    private val urlInterceptor: DynamicUrlInterceptor
) : ViewModel() {

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val connectionStatus = _connectionStatus.asStateFlow()

    fun testConnection(url: String, user: String, pass: String) {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.Loading
            if (!url.startsWith("http")) {
                _connectionStatus.value = ConnectionStatus.Error("La URL debe empezar con http:// o https://")
                return@launch
            }

            val formattedUrl = if (url.endsWith("/")) url else "$url/"

            try {
                val cleanUrl = url.trim().lowercase()
                val formattedUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
                urlInterceptor.setBaseUrl(formattedUrl)

                val salt = AuthUtils.generateSalt()
                val token = AuthUtils.generateToken(pass, salt)

                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    api.ping(user, token, salt)
                }

                if (response.response.status == "ok") {
                    _connectionStatus.value = ConnectionStatus.Success
                } else {
                    _connectionStatus.value = ConnectionStatus.Error("Credenciales inválidas")
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.Error("Fallo de conexión: ${e.localizedMessage}")
            }
        }
    }
    fun saveServer(name: String, url: String, user: String, pass: String) {
        viewModelScope.launch {
            val salt = AuthUtils.generateSalt()
            val token = AuthUtils.generateToken(pass, salt)
            val formattedUrl = if (url.endsWith("/")) url else "$url/"

            val newServer = ServerEntity(
                name = name,
                url = formattedUrl,
                username = user,
                token = token,
                salt = salt,
                isActive = true
            )

            serverDao.insertServer(newServer)
        }
    }

    fun resetStatus() {
        _connectionStatus.value = ConnectionStatus.Idle
    }
}