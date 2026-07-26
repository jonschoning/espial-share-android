package com.espial.espialshare

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager

class AddActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loadingView: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this)
        webView.apply {
            settings.loadsImagesAutomatically = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loadingView.visibility = View.GONE
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError? ) {
                    super.onReceivedError(view, request, error)
                    Log.e("AddActivity::WebViewClient::onReceivedError", "errorCode:${error?.errorCode} description:${error?.description}")
                    loadingView.visibility = View.GONE
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
        loadingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(ProgressBar(this@AddActivity).apply { isIndeterminate = true })
            addView(
                TextView(this@AddActivity).apply {
                    text = getString(R.string.canonicalizing_url)
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 24
                },
            )
        }
        val container = FrameLayout(this)
        container.addView(webView)
        container.addView(
            loadingView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        setContentView(container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        handleIntents()
    }

    override fun onNewIntent(intent: Intent) {
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
        val canonicalizeUrls = sharedPreferences.getBoolean("espial_canonicalize_urls", true)
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val espialCore = EspialCore()
                    val addParams = espialCore.toAddParams(intent)
                    if (canonicalizeUrls && espialCore.hasSiteRule(addParams)) {
                        loadingView.visibility = View.VISIBLE
                        Thread {
                            val resolvedParams = espialCore.resolveAddParams(addParams, canonicalizeUrls)
                            val espialUrl = espialCore.toEspialGetUrl(espialServerUrl, resolvedParams)
                            runOnUiThread { webView.loadUrl(espialUrl) }
                        }.start()
                    } else {
                        webView.loadUrl(espialCore.toEspialGetUrl(espialServerUrl, addParams))
                    }
                }
            }
        }
    }

}