package com.espial.espialshare

import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.preference.PreferenceManager
import java.net.URLEncoder

class EspialCore {
    fun toEspialGetUrl(espialServerUrl: String, intent: Intent): String {
        val addParams = toAddParams(intent)
        return toEspialGetUrl(espialServerUrl, addParams)
    }

    private fun toEspialGetUrl(espialServerUrl: String, addParams: AddParams): String {

        return when (addParams) {
            is AddParams.Bookmark ->
                "$espialServerUrl/add?url=${enc(addParams.Url)}&title=${enc(addParams.Title)}&description=${enc(addParams.Description)}&next=closeWindow"
            is AddParams.Note ->
                "$espialServerUrl/notes/add?title=${enc(addParams.Title)}&description=${enc(addParams.Description)}&next=closeWindow"
        }
    }

    fun toAddParams(intent: Intent): AddParams {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""

        val res = extraText.split('\n')
        return if(res.count() > 1) {
            val lastUrl = res.last().trim().takeWhile { it != '#' }
            if(isUrl(lastUrl)) {
                val content = res.dropLast(1).joinToString("\n").removeSurrounding("\"")
                if(content.length < 80) {
                    AddParams.Bookmark(lastUrl, content, "")
                } else {
                    AddParams.Bookmark(lastUrl, "", content)
                }
            } else {
                AddParams.Note(extraSubject, extraText)
            }
        } else if(isUrl(extraText)) {
            AddParams.Bookmark(extraText, extraSubject, "")
        } else {
            AddParams.Note(extraSubject, extraText)
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