package com.example.allianceautologin

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableActivityAnimations()

        val credentials = CredentialsStore(this)

        if (!credentials.isConfigured()) {
            Toast.makeText(
                applicationContext,
                "Please configure Alliance login credentials",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            disableActivityAnimations()
            return
        }

        // Execute simulated browser login in background coroutine
        lifecycleScope.launch {
            val result = AllianceLoginClient.simulateLogin(
                username = credentials.username,
                password = credentials.password,
                portalUrl = credentials.loginUrl
            )

            val toastText = when (result) {
                is LoginResult.Success -> {
                    if (!result.clientName.isNullOrBlank()) {
                        "Alliance: Logged in (${result.clientName})"
                    } else {
                        "Alliance: Login successful!"
                    }
                }
                is LoginResult.AlreadyLoggedIn -> {
                    if (!result.clientName.isNullOrBlank()) {
                        "Alliance: Already logged in (${result.clientName})"
                    } else {
                        "Alliance: Already connected & logged in"
                    }
                }
                is LoginResult.Failure -> {
                    "Alliance Login Failed: ${result.reason}"
                }
                is LoginResult.NetworkError -> {
                    "Alliance: ${result.message}"
                }
            }

            Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT).show()
            finish()
            disableActivityAnimations()
        }
    }

    private fun disableActivityAnimations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
