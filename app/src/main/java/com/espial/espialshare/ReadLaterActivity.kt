package com.espial.espialshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.os.HandlerCompat
import androidx.preference.PreferenceManager
import org.chromium.net.*
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors


class ReadLaterActivity : Activity() {
    private val TAG = "ReadLaterActivity"
    private lateinit var cronetEngine: CronetEngine
    private var executor: Executor = Executors.newSingleThreadExecutor()
    private lateinit var mainThreadHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cronetEngine = CronetEngine.Builder(this)
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024.toLong())
            .enableHttp2(true)
            .enableQuic(true)
            .build()
        executor = Executors.newSingleThreadExecutor()
        mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper())
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
        if (espialServerUrl == null || espialServerUrl.isEmpty()) {
            Toast.makeText(this, R.string.no_server_url, Toast.LENGTH_LONG).show()
            return
        }
        val espialApiKey = sharedPreferences.getString("espial_api_key", "")
        if (espialApiKey == null || espialApiKey.isEmpty()) {
            Toast.makeText(this, R.string.no_api_key, Toast.LENGTH_LONG).show()
            return
        }

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    val addParams = EspialCore().toAddParams(intent)
                    val postData = toPostData(addParams)
                    if (postData == null) {
                        finish()
                        return
                    }
                    toRequest(espialServerUrl, espialApiKey, postData)
                        .start()
                }
            }
        }
    }

    private fun toRequest( espialServerUrl: String, espialApiKey: String, postData: JSONObject ): UrlRequest {
        val requestCallback = EspialAddRequestCallback(mainThreadHandler) {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            finish()
        }
        val url = "${espialServerUrl.trim().trimEnd('/')}/api/add"
        val requestBuilder = cronetEngine.newUrlRequestBuilder(url, requestCallback, executor)
        requestBuilder.setHttpMethod("POST")
        requestBuilder.addHeader("Content-Type", "application/json; charset=UTF-8")
        requestBuilder.addHeader("Authorization", "ApiKey $espialApiKey")
        requestBuilder.setUploadDataProvider(
            StringUploadDataProvider(postData.toString()),
            executor
        )
        return requestBuilder.build()
    }

    private fun toPostData(addParams: EspialCore.AddParams): JSONObject? {
        when (addParams) {
            is EspialCore.AddParams.Bookmark -> {
                val postData = JSONObject()
                postData.put("url", addParams.Url)
                postData.put("title", addParams.Title)
                postData.put("description", addParams.Description)
                postData.put("toread", true)
                return postData
            }
            is EspialCore.AddParams.Note -> {
                Toast.makeText(
                    this,
                    "Espial Share: Read Later for Notes not currently supported",
                    Toast.LENGTH_LONG
                ).show()
                return null
            }
        }
    }

    class EspialAddRequestCallback(private val handler: Handler, private val cb: (String) -> Unit) : UrlRequest.Callback() {
        private val TAG = "EspialAddRequestCallback"
        override fun onSucceeded(request: UrlRequest?, info: UrlResponseInfo?) {
            Log.i(TAG, "onSucceeded: ${info?.httpStatusCode}")
            handler.post { cb("Espial Share: Created bookmark (read-later )") }
        }

        override fun onFailed( request: UrlRequest?, info: UrlResponseInfo?, error: CronetException? ) {
            Log.i(TAG, "onFailed: ${info?.httpStatusCode.toString()}")
            handler.post { cb("Espial Share: Error creating bookmark: ${info?.httpStatusCode}") }
        }

        override fun onResponseStarted(request: UrlRequest?, info: UrlResponseInfo?) {
            Log.i(TAG, "onResponseStarted: ${info?.httpStatusCode.toString()}")
            handler.post {
                if ((info?.httpStatusCode!! >= 200) && (info.httpStatusCode < 300)) {
                    if (info.httpStatusCode >= 204) {
                        cb("Espial Share: bookmark previously saved")
                    } else {
                        cb("Espial Share: created bookmark (read later)")
                    }
                } else if ((info.httpStatusCode >= 300) && (info.httpStatusCode < 400) || (info.httpStatusCode == 401)) {
                    cb("Espial Share: Error creating bookmark: Unauthorized. Verify ApiKey}")
                } else {
                    cb("Espial Share: Error creating bookmark: httpStatusCode: ${info.httpStatusCode}")
                }
            }
        }

        override fun onReadCompleted( request: UrlRequest?, info: UrlResponseInfo?, byteBuffer: ByteBuffer? ) {
            Log.i(TAG, "onReadCompleted: ${info?.httpStatusCode.toString()}")
            handler.post { cb("") }
        }

        override fun onRedirectReceived( request: UrlRequest?, info: UrlResponseInfo?, newLocationUrl: String? ) {
            Log.i(TAG, "onRedirectReceived: ${info?.httpStatusCode.toString()}")
            handler.post { cb("Espial Share: Error creating bookmark: Unauthorized. Verify ApiKey") }
        }
    }

    class StringUploadDataProvider(input: String) : UploadDataProvider() {
        private val charset = StandardCharsets.UTF_8
        private val inputBytes = input.toByteArray(charset)
        override fun getLength(): Long { return inputBytes.size.toLong() }
        override fun rewind(uploadDataSink: UploadDataSink?) { uploadDataSink!!.onRewindSucceeded() }
        override fun read(uploadDataSink: UploadDataSink?, byteBuffer: ByteBuffer?) {
            byteBuffer!!.put(inputBytes)
            uploadDataSink!!.onReadSucceeded(false)
        }
    }

}