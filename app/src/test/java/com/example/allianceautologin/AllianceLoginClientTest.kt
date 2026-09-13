package com.example.allianceautologin

import org.junit.Assert.*
import org.junit.Test

class AllianceLoginClientTest {

    private val loggedInHtmlSample = """
        <!DOCTYPE html>
        <html>
        <head><title>IPACCT | User information portal</title></head>
        <body>
        <div class="username">
        <p>Welcome, 
        <p>JOHN DOE
        <form method=post><br>
        <input class="ask3" type=submit name=logout value="Click here to logout" style="font-face: verdana; font-size: 14px">
        </form>
        </div>
        <table id="thesmalltable">
            <tr><td width="100"><p><span>Name:</span></p></td><td>JOHN DOE</td></tr>
            <tr><td><p><span>CLIENT ID:</span></p></td><td>12345678901</td></tr>
            <tr><td><p><span>Package:</span></p></td><td>STARTER</td></tr>
        </table>
        Account status: Active
        </body>
        </html>
    """.trimIndent()

    private val loggedOutHtmlSample = """
        <!DOCTYPE html>
        <html>
        <head><title>IPACCT Login</title></head>
        <body>
        <form method="post" action="/0/up/">
            <input type="hidden" name="authenticity_token" value="sec_tok_98765">
            <input type="text" name="user" value="">
            <input type="password" name="pass" value="">
            <input type="submit" name="login" value="Login">
        </form>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun testIsUserLoggedIn_withLoggedInHtml_returnsTrue() {
        assertTrue(AllianceLoginClient.isUserLoggedIn(loggedInHtmlSample))
    }

    @Test
    fun testIsUserLoggedIn_withLoggedOutHtml_returnsFalse() {
        assertFalse(AllianceLoginClient.isUserLoggedIn(loggedOutHtmlSample))
    }

    @Test
    fun testExtractClientName_fromRealAllianceHtml() {
        val name = AllianceLoginClient.extractClientName(loggedInHtmlSample)
        assertEquals("JOHN DOE", name)
    }

    @Test
    fun testExtractHiddenInputs() {
        val inputs = AllianceLoginClient.extractHiddenInputs(loggedOutHtmlSample)
        assertTrue(inputs.containsKey("authenticity_token"))
        assertEquals("sec_tok_98765", inputs["authenticity_token"])
    }

    @Test
    fun testResolveActionUrl() {
        val base = "http://10.254.254.57/0/up/"
        val resolved = AllianceLoginClient.resolveActionUrl(base, "/0/up/")
        assertEquals("http://10.254.254.57/0/up/", resolved)

        val resolvedRel = AllianceLoginClient.resolveActionUrl(base, "authenticate")
        assertEquals("http://10.254.254.57/0/up/authenticate", resolvedRel)
    }

    @Test
    fun testExtractFailureReason() {
        val failHtml = "<div>Invalid username or password entered</div>"
        val reason = AllianceLoginClient.extractFailureReason(failHtml)
        assertEquals("Invalid username or password", reason)
    }
}
