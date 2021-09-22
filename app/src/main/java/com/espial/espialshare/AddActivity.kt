package com.espial.espialshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.webkit.*
import androidx.preference.PreferenceManager
import java.net.URLEncoder

class AddActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.apply {
            settings.loadsImagesAutomatically = true
            settings.javaScriptEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError? ) {
                    super.onReceivedError(view, request, error)
                    Log.e("AddActivity::WebViewClient::onReceivedError", "errorCode:${error?.errorCode} description:${error?.description}")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(w: WebView?) {
                    super.onCloseWindow(w);
                    finish()
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    var ret = super.onConsoleMessage(consoleMessage)
                    if(consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.WARNING &&
                        consoleMessage?.message().startsWith("Scripts may close only the windows that were opened by them.", true)) {
                        Log.d("AddActivity::WebChromeClient::onConsoleMessage", "Detected window script close attempt, finishing activity")
                        finish()
                    }
                    return ret;
                }
            }
        }
        setContentView(webView)

        handleIntents()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntents()
    }

    private fun handleIntents() {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val espialUrl = toEspialUrl(intent)
                    webView.loadUrl(espialUrl)
                }
            }
        }
    }

    private fun toEspialUrl(intent: Intent): String {
        val addParams = toAddParams(intent)
        return toEspialUrl(addParams)
    }

    private fun toEspialUrl(addParams: AddParams): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val espialServerUrl = sharedPreferences.getString("espial_server_url", "") ?: ""

        return when (addParams) {
            is AddParams.Bookmark ->
                "$espialServerUrl/add?url=${addParams.Url}&title=${addParams.Title}&description=${addParams.Description}&next=closeWindow"
            is AddParams.Note ->
                "$espialServerUrl/notes/add?title=${addParams.Title}&description=${addParams.Description}&next=closeWindow"
        }
    }

    private fun toAddParams(intent: Intent): AddParams {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

        val res = extraText.split('\n')
        return if(res.count() > 1) {
            val lastUrl = res.last().trim().takeWhile { it != '#' }
            if(isUrl(lastUrl)) {
                val description = res.dropLast(1).joinToString("\n").removeSurrounding("\"")
                AddParams.Bookmark(enc(lastUrl), "", enc(description))
            } else {
                AddParams.Note(enc(extraSubject), enc(extraText))
            }
        } else if(isUrl(extraText)) {
            AddParams.Bookmark(enc(extraText), enc(extraSubject), "")
        } else {
            AddParams.Note(enc(extraSubject), enc(extraText))
        }
    }

    private fun enc(s: String) =
            URLEncoder.encode(s, "utf-8")

    private fun isUrl(potentialUrl: String): Boolean =
            Patterns.WEB_URL.matcher(potentialUrl).matches()

    sealed class AddParams {
        data class Bookmark (val Url: String, val Title: String, val Description: String) : AddParams()
        data class Note (val Title: String, val Description: String) : AddParams()
    }
}