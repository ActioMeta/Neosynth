package com.example.neosynth.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.neosynth.data.local.entities.ServerEntity
import kotlinx.coroutines.launch
import java.security.MessageDigest
import androidx.compose.ui.res.stringResource
import com.example.neosynth.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditServerDialog(
    server: ServerEntity,
    onDismiss: () -> Unit,
    onServerUpdated: (ServerEntity) -> Unit
) {
    var serverName by remember { mutableStateOf(server.name) }
    var serverUrl by remember { mutableStateOf(server.url) }
    var username by remember { mutableStateOf(server.username) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.server_edit_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nombre del servidor
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { 
                        serverName = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.server_name_label)) },
                    placeholder = { Text(stringResource(R.string.my_server_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Label, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                
                // URL del servidor
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { 
                        serverUrl = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.server_url_label)) },
                    placeholder = { Text(stringResource(R.string.server_url_placeholder)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Dns, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                // Usuario
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.server_username)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Person, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                // Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.server_password_optional_label)) },
                    placeholder = { Text(stringResource(R.string.server_password_optional_hint)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (passwordVisible) stringResource(R.string.content_desc_hide_password) else stringResource(R.string.content_desc_show_password)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                // Error message
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (serverName.isBlank() || serverUrl.isBlank() || username.isBlank()) {
                            errorMessage = context.getString(R.string.error_fields_required_edit)
                            return@launch
                        }

                        isLoading = true
                        try {
                            val salt: String
                            val token: String
                            
                            if (password.isNotBlank()) {
                                // Generate new salt and token for Subsonic API
                                salt = generateSalt()
                                token = generateToken(password, salt)
                            } else {
                                // Keep old token and salt
                                salt = server.salt
                                token = server.token
                            }

                            val updatedServer = server.copy(
                                name = serverName.trim(),
                                url = serverUrl.trim().removeSuffix("/"),
                                username = username.trim(),
                                token = token,
                                salt = salt
                            )

                            onServerUpdated(updatedServer)
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun generateSalt(): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..16)
        .map { chars.random() }
        .joinToString("")
}

private fun generateToken(password: String, salt: String): String {
    val input = password + salt
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
