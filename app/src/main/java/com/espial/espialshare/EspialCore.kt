package com.espial.espialshare

import android.content.Intent
import android.text.Html
import android.util.Patterns
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class EspialCore {
    fun toEspialGetUrl(espialServerUrl: String, addParams: AddParams): String {
        return when (addParams) {
            is AddParams.Bookmark ->
                "$espialServerUrl/add?url=${enc(addParams.url)}&title=${enc(addParams.title)}&description=${enc(addParams.description)}&next=closeWindow"
            is AddParams.Note ->
                "$espialServerUrl/notes/add?title=${enc(addParams.title)}&description=${enc(addParams.description)}&next=closeWindow"
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

    // Whether resolveAddParams has site-specific handling for this URL (a host in SITE_RULES).
    // Local/cheap: lets callers decide up front whether to show progress UI.
    fun hasSiteRule(addParams: AddParams): Boolean =
        when (addParams) {
            is AddParams.Bookmark -> {
                val host = try { URL(addParams.url).host?.lowercase() } catch (e: Exception) { null }
                host != null && siteRuleFor(host) != null
            }
            is AddParams.Note -> false
        }

    // Blocking network calls; run off the main thread. Resolves share-target redirect URLs
    // (and a title) from the phone, where the espial server may be blocked or rate limited.
    fun resolveAddParams(addParams: AddParams, canonicalizeUrls: Boolean): AddParams {
        if (!canonicalizeUrls) return addParams
        return when (addParams) {
            is AddParams.Bookmark -> {
                val (finalUrl, fetchedTitle) = resolveUrlAndTitle(addParams.url, needTitle = addParams.title.isBlank())
                addParams.copy(url = finalUrl, title = fetchedTitle ?: addParams.title)
            }
            is AddParams.Note -> addParams
        }
    }

    // Walks redirects one hop at a time so the final hop's body yields the title without a
    // second request. Uses HEAD when no title is needed, or when the site has a titleFetcher.
    private fun resolveUrlAndTitle(url: String, needTitle: Boolean): Pair<String, String?> {
        var current = url
        repeat(MAX_REDIRECT_HOPS) {
            val parsedCurrent = try { URL(current) } catch (e: Exception) { return finishResolution(current, needTitle) }
            val rule = siteRuleFor(parsedCurrent.host?.lowercase() ?: "")
            val fetchBody = needTitle && rule?.titleFetcher == null
            val connection = try {
                parsedCurrent.openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return finishResolution(current, needTitle)
            }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = RESOLVE_TIMEOUT_MS
                connection.readTimeout = RESOLVE_TIMEOUT_MS
                connection.requestMethod = if (fetchBody) "GET" else "HEAD"
                connection.setRequestProperty("User-Agent", RESOLVE_USER_AGENT)
                connection.setRequestProperty("Cache-Control", "max-age=0")
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return finishResolution(current, needTitle)
                    val nextUrl = URL(URL(current), location)
                    val nextRule = siteRuleFor(nextUrl.host?.lowercase() ?: "")
                    if (nextRule != null && nextRule.isDeadEnd(nextUrl.path ?: "")) {
                        // Redirect goes somewhere useless (e.g. Reddit's login wall), so keep
                        // `current` rather than saving that page as the bookmark.
                        return finishResolution(current, needTitle)
                    }
                    current = nextUrl.toString()
                } else if (fetchBody && code == HttpURLConnection.HTTP_OK) {
                    val html = String(readBounded(connection.inputStream, MAX_TITLE_FETCH_BYTES), Charsets.UTF_8)
                    return finalizeUrl(current) to extractTitle(html)
                } else {
                    return finishResolution(current, needTitle)
                }
            } catch (e: Exception) {
                return finishResolution(current, needTitle)
            } finally {
                connection.disconnect()
            }
        }
        return finishResolution(current, needTitle)
    }

    // Finalizes the URL and, if a title is still missing, gets it from the SiteRule's
    // titleFetcher instead of the page body - also the fallback path after a dead end/error.
    private fun finishResolution(url: String, needTitle: Boolean): Pair<String, String?> {
        val finalUrl = finalizeUrl(url)
        val host = try { URL(finalUrl).host?.lowercase() } catch (e: Exception) { null }
        val title = if (needTitle && host != null) siteRuleFor(host)?.titleFetcher?.invoke(finalUrl) else null
        return finalUrl to title
    }

    // Per-site request/URL adjustments, keyed by host.
    // Mirrors the server-side RequestOverride in Handler.Add.
    private data class SiteRule(
        val hosts: Set<String>,
        // Host the saved URL is normalized to, applied once at the end of the chain.
        val canonicalHost: String? = null,
        val isTrackingParam: (key: String) -> Boolean = { false },
        // Redirect targets not worth following, e.g. a login wall.
        val isDeadEnd: (path: String) -> Boolean = { false },
        // Title source other than the page body, e.g. an oembed API.
        val titleFetcher: ((url: String) -> String?)? = null,
    )

    private fun siteRuleFor(host: String): SiteRule? = SITE_RULES.find { host in it.hosts }

    private fun finalizeUrl(url: String): String {
        val parsed = try { URL(url) } catch (e: Exception) { return url }
        val host = parsed.host?.lowercase() ?: return url
        val rule = siteRuleFor(host) ?: return url
        val keptQuery = parsed.query?.split("&")?.filter { param ->
            !rule.isTrackingParam(param.substringBefore("=").lowercase())
        }?.joinToString("&").orEmpty()
        val newFile = parsed.path + if (keptQuery.isNotEmpty()) "?$keptQuery" else ""
        return URL(parsed.protocol, rule.canonicalHost ?: parsed.host, parsed.port, newFile).toString()
    }

    private fun extractTitle(html: String): String? {
        val rawTitle = TITLE_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return Html.fromHtml(rawTitle.trim(), Html.FROM_HTML_MODE_LEGACY).toString().trim().ifEmpty { null }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        input.use {
            var total = 0
            while (total < maxBytes) {
                val read = it.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                total += read
            }
        }
        return output.toByteArray()
    }

    sealed class AddParams {
        data class Bookmark (val url: String, val title: String, val description: String) : AddParams()
        data class Note (val title: String, val description: String) : AddParams()
    }

    companion object {
        private const val MAX_REDIRECT_HOPS = 5
        private const val RESOLVE_TIMEOUT_MS = 4000
        private const val MAX_TITLE_FETCH_BYTES = 1024 * 1024
        private const val RESOLVE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
        private val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        // Reddit page fetches don't yield a title (JS challenge / login wall), but its
        // public oembed endpoint returns the post title unauthenticated.
        private fun redditOembedTitle(url: String): String? {
            val oembedUrl = "https://www.reddit.com/oembed?url=${URLEncoder.encode(url, "utf-8")}&format=json"
            val connection = try {
                URL(oembedUrl).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return null
            }
            return try {
                connection.connectTimeout = RESOLVE_TIMEOUT_MS
                connection.readTimeout = RESOLVE_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", RESOLVE_USER_AGENT)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                val body = connection.inputStream.use { it.readBytes() }
                JSONObject(String(body, Charsets.UTF_8)).optString("title").trim().ifEmpty { null }
            } catch (e: Exception) {
                null
            } finally {
                connection.disconnect()
            }
        }

        private val SITE_RULES = listOf(
            SiteRule(
                hosts = setOf("reddit.com", "www.reddit.com", "np.reddit.com", "old.reddit.com"),
                canonicalHost = "www.reddit.com",
                isTrackingParam = { key -> key == "share_id" || key.startsWith("utm_") },
                isDeadEnd = { path -> path.startsWith("/login") },
                titleFetcher = ::redditOembedTitle,
            ),
        )
    }

}