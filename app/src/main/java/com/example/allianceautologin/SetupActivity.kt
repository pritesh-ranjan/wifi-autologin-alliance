package com.example.allianceautologin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = CredentialsStore(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SetupScreen(
                        initialUser = store.username,
                        initialPass = store.password,
                        initialUrl = store.loginUrl,
                        onSave = { user, pass, url ->
                            store.save(user, pass, url)
                            Toast.makeText(
                                applicationContext,
                                "Credentials saved! Next time, app will auto-login with no UI.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onTestLogin = { user, pass, url, onResult ->
                            AllianceLoginClient.simulateLogin(user, pass, url).let { result ->
                                onResult(result)
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initialUser: String,
    initialPass: String,
    initialUrl: String,
    onSave: (String, String, String) -> Unit,
    onTestLogin: suspend (String, String, String, (LoginResult) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    var username by remember { mutableStateOf(initialUser) }
    var password by remember { mutableStateOf(initialPass) }
    var url by remember { mutableStateOf(initialUrl) }
    var passwordVisible by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccess by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Alliance Auto Login",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Configure credentials once. The app will subsequently auto-login with no UI and auto-close instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Client ID / Username") },
                    placeholder = { Text("e.g. 13012147108") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Hide" else "Show", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Login Portal URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (statusMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (statusSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = statusMessage ?: "",
                    modifier = Modifier.padding(12.dp),
                    color = if (statusSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save & Test Login Button
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    statusMessage = "Please provide both Username and Password"
                    statusSuccess = false
                    return@Button
                }
                onSave(username, password, url)

                isTesting = true
                statusMessage = "Testing simulated login..."
                statusSuccess = false

                coroutineScope.launch {
                    onTestLogin(username, password, url) { result ->
                        isTesting = false
                        when (result) {
                            is LoginResult.Success -> {
                                statusSuccess = true
                                statusMessage = "Success! Logged in as ${result.clientName ?: username}"
                            }
                            is LoginResult.AlreadyLoggedIn -> {
                                statusSuccess = true
                                statusMessage = "Connected! Already logged in as ${result.clientName ?: username}"
                            }
                            is LoginResult.Failure -> {
                                statusSuccess = false
                                statusMessage = "Login failed: ${result.reason}"
                            }
                            is LoginResult.NetworkError -> {
                                statusSuccess = false
                                statusMessage = "Network error: ${result.message}"
                            }
                        }
                    }
                }
            },
            enabled = !isTesting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing Login...")
            } else {
                Text("Save & Test Login")
            }
        }

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tip: You can long-press the app icon on your home screen and tap 'Settings' anytime to update credentials.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
