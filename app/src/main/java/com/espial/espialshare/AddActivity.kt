package com.espial.espialshare

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(w: WebView?) {
                    super.onCloseWindow(w);
                    finish()
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
                    val intentTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                    val loadUrl = "$espialServerUrl/add?_hasData&url=$intentUrl&title=$intentTitle"
                    webView.loadUrl(loadUrl)
                }
            }
        }
    }

}