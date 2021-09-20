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
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

        val fromChromeSelection = tryParseChromeSelection(extraText)
        val addParams = if(fromChromeSelection != null) {
            AddParams(
                URLEncoder.encode(fromChromeSelection.first, "utf-8"),
                "",
                URLEncoder.encode(fromChromeSelection.second, "utf-8")
            )
        } else {
            AddParams(
                URLEncoder.encode(extraText, "utf-8"),
                URLEncoder.encode(extraSubject, "utf-8"),
                ""
            )
        }
        return toEspialUrl(addParams)
    }

    private fun toEspialUrl(addParams: AddParams): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val espialServerUrl = sharedPreferences.getString("espial_server_url", "") ?: ""

        return "$espialServerUrl/add?_hasData&url=${addParams.Url}&title=${addParams.Title}&description=${addParams.Description}"
    }

    private fun tryParseChromeSelection(srcText: String): Pair<String, String>? {
        val res = srcText.split('\n')
        if(res.count() > 1) {
            val potentialUrl = res.last().trim().takeWhile { it != '#' }
            if(Patterns.WEB_URL.matcher(potentialUrl).matches()) {
                val description = res.dropLast(1).joinToString("\n").removeSurrounding("\"")
                return Pair(potentialUrl, description)
            }
        }
        return null
    }

    class AddParams (val Url: String, val Title: String, val Description: String)
}