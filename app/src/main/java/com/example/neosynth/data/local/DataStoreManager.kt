package com.example.neosynth.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "server_settings")

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val URL_KEY = stringPreferencesKey("server_url")
    private val USER_KEY = stringPreferencesKey("username")
    private val TOKEN_KEY = stringPreferencesKey("token")
    private val SALT_KEY = stringPreferencesKey("salt")

    // Flujo para leer la URL (actúa como un observable)
    val serverUrl: Flow<String?> = context.dataStore.data.map { pref -> pref[URL_KEY] }

    // Función para guardar todo el perfil de conexión
    suspend fun saveServerSettings(url: String, user: String, token: String, salt: String) {
        context.dataStore.edit { pref ->
            pref[URL_KEY] = url
            pref[USER_KEY] = user
            pref[TOKEN_KEY] = token
            pref[SALT_KEY] = salt
        }
    }
}