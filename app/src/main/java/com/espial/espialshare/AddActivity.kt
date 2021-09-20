package com.espial.espialshare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.webkit.*
import androidx.preference.PreferenceManager

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
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val espialServerUrl = sharedPreferences.getString("espial_server_url", "")

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val intentUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                    val intentSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                    val loadUrl = "$espialServerUrl/add?_hasData&url=$intentUrl&title=${intentSubject}"
                    webView.loadUrl(loadUrl)
                }
            }
        }
    }

}