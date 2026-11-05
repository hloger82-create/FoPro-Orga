package de.naturfoto.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.naturfoto.app.theme.translucentSurface
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var confirmHint by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(translucentSurface())
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (mode == AuthMode.LOGIN) "Anmelden" else "Registrieren",
                style = MaterialTheme.typography.headlineMedium,
            )

            if (mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Anzeigename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-Mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (confirmHint) {
                Text(
                    "Registrierung fast fertig: Wir haben dir eine Bestätigungs-Mail an ${email.trim()} geschickt. Bitte bestätige deine E-Mail-Adresse und melde dich dann an.",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            if (mode == AuthMode.LOGIN) {
                                repo.signIn(email.trim(), password)
                                onSignedIn()
                            } else {
                                repo.signUp(email.trim(), password, username.trim(), displayName.trim())
                                confirmHint = true
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Unbekannter Fehler"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (mode == AuthMode.LOGIN) "Anmelden" else "Registrieren")
                }
            }

            TextButton(onClick = { mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN }) {
                Text(
                    if (mode == AuthMode.LOGIN) "Neu hier? Konto erstellen" else "Schon registriert? Anmelden",
                )
            }
        }
    }
}

private enum class AuthMode { LOGIN, REGISTER }
