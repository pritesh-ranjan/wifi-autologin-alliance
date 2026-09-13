package com.example.allianceautologin

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class LoginResult {
    data class Success(val clientName: String? = null) : LoginResult()
    data class AlreadyLoggedIn(val clientName: String? = null) : LoginResult()
    data class Failure(val reason: String) : LoginResult()
    data class NetworkError(val message: String) : LoginResult()
}

object AllianceLoginClient {

    private const val TAG = "AllianceLoginClient"

    // Modern mobile Chrome User-Agent string
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /**
     * In-memory thread-safe cookie jar simulating browser cookie persistence across requests
     */
    private class InMemoryCookieJar : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val currentList = cookieStore.getOrPut(host) { mutableListOf() }
            for (newCookie in cookies) {
                currentList.removeAll { it.name == newCookie.name }
                currentList.add(newCookie)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val currentTime = System.currentTimeMillis()
            val valid = cookieStore[url.host]?.filter { it.expiresAt > currentTime } ?: emptyList()
            return valid
        }

        @Synchronized
        fun clear() {
            cookieStore.clear()
        }
    }

    private val cookieJar = InMemoryCookieJar()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Executes the login workflow simulating real user interaction in a browser:
     * 1. Loads portal page (GET) with complete browser headers
     * 2. Checks if already authenticated
     * 3. Parses any hidden tokens or preset form inputs
     * 4. Submits login form (POST) with all parameters, Referer, and Origin
     * 5. Validates post-login response
     */
    suspend fun simulateLogin(
        username: String,
        password: String,
        portalUrl: String = CredentialsStore.DEFAULT_LOGIN_URL
    ): LoginResult = withContext(Dispatchers.IO) {
        val sanitizedUrl = if (portalUrl.endsWith("/")) portalUrl else "$portalUrl/"
        val httpUrl = sanitizedUrl.toHttpUrlOrNull()
            ?: return@withContext LoginResult.Failure("Invalid Portal URL: $sanitizedUrl")

        val origin = "${httpUrl.scheme}://${httpUrl.host}${if (httpUrl.port != 80 && httpUrl.port != 443) ":${httpUrl.port}" else ""}"

        try {
            // STEP 1: Pre-flight GET request simulating user opening the login page
            Log.d(TAG, "Step 1: Fetching login page from $sanitizedUrl")
            val getRequest = Request.Builder()
                .url(sanitizedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Connection", "keep-alive")
                .header("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("Sec-Ch-Ua-Mobile", "?1")
                .header("Sec-Ch-Ua-Platform", "\"Android\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .get()
                .build()

            val getResponse = httpClient.newCall(getRequest).execute()
            val getResponseBody = getResponse.body?.string() ?: ""

            if (!getResponse.isSuccessful && getResponse.code !in 200..399) {
                return@withContext LoginResult.Failure("Server responded with HTTP ${getResponse.code}")
            }

            // Check if already authenticated
            if (isUserLoggedIn(getResponseBody)) {
                val clientName = extractClientName(getResponseBody)
                Log.d(TAG, "Already logged in as: $clientName")
                return@withContext LoginResult.AlreadyLoggedIn(clientName)
            }

            // Extract hidden inputs and form action if present
            val hiddenInputs = extractHiddenInputs(getResponseBody)
            val actionUrl = resolveActionUrl(sanitizedUrl, extractFormAction(getResponseBody))

            // STEP 2: POST form submission with credentials and browser headers
            Log.d(TAG, "Step 2: Submitting login credentials to $actionUrl")
            val formBuilder = FormBody.Builder()
            // Add all parsed hidden inputs first
            for ((key, value) in hiddenInputs) {
                if (key != "user" && key != "pass" && key != "login") {
                    formBuilder.add(key, value)
                }
            }
            // Standard Alliance / IPACCT login parameters
            formBuilder.add("user", username)
            formBuilder.add("pass", password)
            formBuilder.add("login", "Login")

            val postRequest = Request.Builder()
                .url(actionUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", origin)
                .header("Referer", sanitizedUrl)
                .header("Connection", "keep-alive")
                .header("Cache-Control", "max-age=0")
                .header("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .header("Sec-Ch-Ua-Mobile", "?1")
                .header("Sec-Ch-Ua-Platform", "\"Android\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .post(formBuilder.build())
                .build()

            val postResponse = httpClient.newCall(postRequest).execute()
            val postResponseBody = postResponse.body?.string() ?: ""

            // STEP 3: Validate post-login state
            if (isUserLoggedIn(postResponseBody)) {
                val clientName = extractClientName(postResponseBody)
                Log.d(TAG, "Login successful for: $clientName")
                return@withContext LoginResult.Success(clientName)
            }

            // Check for known error indicators
            val failureReason = extractFailureReason(postResponseBody)
            Log.w(TAG, "Login failed: $failureReason")
            return@withContext LoginResult.Failure(failureReason)

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout", e)
            return@withContext LoginResult.NetworkError("Connection timed out")
        } catch (e: ConnectException) {
            Log.e(TAG, "Connection refused or unreachable", e)
            return@withContext LoginResult.NetworkError("Cannot reach portal (Check Wi-Fi)")
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host", e)
            return@withContext LoginResult.NetworkError("Unknown host: ${httpUrl.host}")
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            return@withContext LoginResult.NetworkError(e.localizedMessage ?: "Network communication error")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            return@withContext LoginResult.Failure(e.localizedMessage ?: "Unexpected error")
        }
    }

    /**
     * Determines whether the HTML page indicates an authenticated session.
     */
    fun isUserLoggedIn(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        return lower.contains("name=\"logout\"") ||
               lower.contains("name=logout") ||
               lower.contains("click here to logout") ||
               lower.contains("account status: active") ||
               lower.contains("your internet connection is configured properly") ||
               (lower.contains("welcome,") && lower.contains("thesmalltable"))
    }

    /**
     * Extracts client name from the user details panel if available.
     */
    fun extractClientName(html: String): String? {
        // Look for: <tr><td ...><span>Name:</span></td><td ...>PRITESH RANJAN</td></tr>
        val pattern = Pattern.compile(
            "name:?\\s*</span>\\s*</td>\\s*<td[^>]*>\\s*([^<]+)</td>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val name = matcher.group(1)?.trim()
            if (!name.isNullOrEmpty() && !name.equals("n/a", ignoreCase = true)) {
                return name
            }
        }

        // Alternative match for Welcome, \n <p>NAME
        val welcomePattern = Pattern.compile(
            "welcome,\\s*(?:<p>)?\\s*([A-Z0-9 ]{2,30})",
            Pattern.CASE_INSENSITIVE
        )
        val welcomeMatcher = welcomePattern.matcher(html)
        if (welcomeMatcher.find()) {
            val name = welcomeMatcher.group(1)?.trim()
            if (!name.isNullOrEmpty()) {
                return name
            }
        }
        return null
    }

    /**
     * Extracts failure description from HTML response.
     */
    fun extractFailureReason(html: String): String {
        val lower = html.lowercase()
        return when {
            lower.contains("invalid username") || lower.contains("invalid password") || lower.contains("incorrect password") ->
                "Invalid username or password"
            lower.contains("expired") ->
                "Account has expired"
            lower.contains("already logged in from another") ->
                "Session already active on another device"
            lower.contains("deactivated") ->
                "Account deactivated"
            else ->
                "Login failed. Check credentials or connection."
        }
    }

    /**
     * Extracts hidden form input names and values from HTML.
     */
    fun extractHiddenInputs(html: String): Map<String, String> {
        val inputs = mutableMapOf<String, String>()
        val inputPattern = Pattern.compile("<input[^>]+>", Pattern.CASE_INSENSITIVE)
        val matcher = inputPattern.matcher(html)

        val namePattern = Pattern.compile("name=[\"']?([^\"'\\s>]+)", Pattern.CASE_INSENSITIVE)
        val valuePattern = Pattern.compile("value=[\"']?([^\"'\\s>]*)", Pattern.CASE_INSENSITIVE)
        val typePattern = Pattern.compile("type=[\"']?([^\"'\\s>]+)", Pattern.CASE_INSENSITIVE)

        while (matcher.find()) {
            val tag = matcher.group(0) ?: continue
            val typeMatcher = typePattern.matcher(tag)
            val isHidden = typeMatcher.find() && typeMatcher.group(1).equals("hidden", ignoreCase = true)

            if (isHidden) {
                val nameMatch = namePattern.matcher(tag)
                val valueMatch = valuePattern.matcher(tag)
                if (nameMatch.find()) {
                    val name = nameMatch.group(1)
                    val value = if (valueMatch.find()) valueMatch.group(1) else ""
                    if (!name.isNullOrEmpty()) {
                        inputs[name] = value ?: ""
                    }
                }
            }
        }
        return inputs
    }

    /**
     * Extracts form action URL from HTML.
     */
    fun extractFormAction(html: String): String? {
        val formPattern = Pattern.compile("<form[^>]+action=[\"']?([^\"'\\s>]+)", Pattern.CASE_INSENSITIVE)
        val matcher = formPattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    /**
     * Resolves relative form action to absolute URL against the base URL.
     */
    fun resolveActionUrl(baseUrl: String, action: String?): String {
        if (action.isNullOrBlank()) return baseUrl
        val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return baseUrl
        val resolved = baseHttpUrl.resolve(action)
        return resolved?.toString() ?: baseUrl
    }
}
