package com.espial.espialshare

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager

class AddActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
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
                    super.onCloseWindow(w)
                    finish()
                }
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val ret = super.onConsoleMessage(consoleMessage)
                    if(consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.WARNING &&
                        consoleMessage.message().startsWith("Scripts may close only the windows that were opened by them.", true)) {
                        Log.d("AddActivity::WebChromeClient::onConsoleMessage", "Detected window script close attempt, finishing activity")
                        finish()
                    }
                    return ret
                }
            }
        }
        val container = FrameLayout(this)
        container.addView(webView)
        setContentView(container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
        if (espialServerUrl.isNullOrEmpty()) {
            Toast.makeText(this, R.string.no_server_url, Toast.LENGTH_LONG).show()
            return
        }
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val espialUrl = EspialCore().toEspialGetUrl(espialServerUrl, intent)
                    webView.loadUrl(espialUrl)
                }
            }
        }
    }

}