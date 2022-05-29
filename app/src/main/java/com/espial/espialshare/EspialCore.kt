package com.espial.espialshare

import android.content.Intent
import android.util.Patterns
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

        var addParams = trySplitAndParseBookmark(extraText, "\n")
        if (addParams != null) return addParams
        addParams = trySplitAndParseBookmark(extraText, " ")
        if (addParams != null) return addParams

        return if (isUrl(extraText)) {
            AddParams.Bookmark(extraText, extraSubject, "")
        } else if (isUrl(extraSubject)) {
            AddParams.Bookmark(extraSubject, extraText, "")
        } else {
            AddParams.Note(extraSubject, extraText)
        }
    }

    private fun trySplitAndParseBookmark(input: String, delimiter: String): AddParams? {
        val tokens = input.split(delimiter)
        if (tokens.count() <= 1) {
            return null
        }
        val lastToken = stripHash(tokens.last())
        if (!isUrl(lastToken)) {
            return null
        }
        val content =
            tokens.dropLast(1).joinToString(delimiter).removeSurrounding("\"")
        return if (content.length < 80) {
            AddParams.Bookmark(lastToken, content, "")
        } else {
            AddParams.Bookmark(lastToken, "", content)
        }
    }

    private fun stripHash(res: String) =
        res.trim().takeWhile { it != '#' }

    private fun enc(s: String) =
        URLEncoder.encode(s, "utf-8")

    private fun isUrl(potentialUrl: String): Boolean =
        Patterns.WEB_URL.matcher(potentialUrl).matches()

    sealed class AddParams {
        data class Bookmark (val Url: String, val Title: String, val Description: String) : AddParams()
        data class Note (val Title: String, val Description: String) : AddParams()
    }

}