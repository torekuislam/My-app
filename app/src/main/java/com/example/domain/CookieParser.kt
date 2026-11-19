package com.example.domain

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class ParsedCookie(
    val name: String,
    val value: String,
    val domain: String = ".facebook.com",
    val path: String = "/",
    val secure: Boolean = true,
)

data class CookieParseResult(
    val cookies: List<ParsedCookie>,
    val normalizedCookieString: String,
    val fbUserId: String?,
    val iUserId: String?,
    val sourceFormat: String,
)

@JsonClass(generateAdapter = true)
data class JsonCookieDto(
    val name: String? = null,
    val value: String? = null,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean? = null,
)

object CookieParser {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonListType = Types.newParameterizedType(List::class.java, JsonCookieDto::class.java)
    private val jsonListAdapter = moshi.adapter<List<JsonCookieDto>>(jsonListType)

    fun parse(rawInput: String): CookieParseResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return CookieParseResult(
                cookies = emptyList(),
                normalizedCookieString = "",
                fbUserId = null,
                iUserId = null,
                sourceFormat = "EMPTY",
            )
        }

        // 1. Try JSON array format (e.g., Cookie-Editor / EditThisCookie export)
        if (trimmed.startsWith("[")) {
            val parsedJson = runCatching { jsonListAdapter.fromJson(trimmed) }.getOrNull()
            if (!parsedJson.isNullOrEmpty()) {
                val cookies = parsedJson.mapNotNull { dto ->
                    val name = dto.name?.trim().orEmpty()
                    val value = dto.value?.trim() ?: ""
                    if (name.isEmpty()) return@mapNotNull null
                    ParsedCookie(
                        name = name,
                        value = value,
                        domain = dto.domain?.takeIf { it.isNotBlank() } ?: ".facebook.com",
                        path = dto.path?.takeIf { it.isNotBlank() } ?: "/",
                        secure = dto.secure ?: true,
                    )
                }
                return buildResult(cookies, "JSON")
            }
        }

        // 2. Try Netscape tab-separated format if multiline with tabs
        if (trimmed.contains("\t") && trimmed.lines().any { !it.startsWith("#") && it.split("\t").size >= 7 }) {
            val cookies = trimmed.lines().mapNotNull { line ->
                val clean = line.trim()
                if (clean.isEmpty() || clean.startsWith("#")) return@mapNotNull null
                val parts = clean.split("\t")
                if (parts.size >= 7) {
                    val domain = parts[0].ifBlank { ".facebook.com" }
                    val path = parts[2].ifBlank { "/" }
                    val secure = parts[3].equals("TRUE", ignoreCase = true)
                    val name = parts[5].trim()
                    val value = parts[6].trim()
                    if (name.isNotEmpty()) {
                        ParsedCookie(name = name, value = value, domain = domain, path = path, secure = secure)
                    } else null
                } else null
            }
            if (cookies.isNotEmpty()) {
                return buildResult(cookies, "NETSCAPE")
            }
        }

        // 3. Standard semicolon-delimited header string (e.g. datr=...; sb=...; c_user=...; xs=...;)
        val cleanedHeader = trimmed
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .replace("\r", "")
            .replace("\n", "; ")

        val seen = LinkedHashMap<String, ParsedCookie>()
        cleanedHeader.split(";").forEach { segment ->
            val token = segment.trim()
            if (token.isEmpty()) return@forEach
            val eqIndex = token.indexOf('=')
            if (eqIndex <= 0) return@forEach
            val name = token.substring(0, eqIndex).trim()
            val value = token.substring(eqIndex + 1).trim()
            if (name.isNotEmpty()) {
                seen[name] = ParsedCookie(
                    name = name,
                    value = value,
                    domain = ".facebook.com",
                    path = "/",
                    secure = true,
                )
            }
        }

        return buildResult(seen.values.toList(), "SEMICOLON")
    }

    private fun buildResult(cookies: List<ParsedCookie>, format: String): CookieParseResult {
        val cUser = cookies.firstOrNull { it.name == "c_user" }?.value?.takeIf { it.isNotBlank() }
        val iUser = cookies.firstOrNull { it.name == "i_user" }?.value?.takeIf { it.isNotBlank() }
        val normalized = cookies.joinToString("; ") { "${it.name}=${it.value}" }
        return CookieParseResult(
            cookies = cookies,
            normalizedCookieString = normalized,
            fbUserId = cUser ?: iUser,
            iUserId = iUser,
            sourceFormat = format,
        )
    }

    fun toSetCookieHeader(cookie: ParsedCookie): String {
        val domain = if (cookie.domain.contains("facebook.com", ignoreCase = true)) {
            ".facebook.com"
        } else {
            cookie.domain
        }
        val secureAttr = if (cookie.secure) "; Secure" else ""
        return "${cookie.name}=${cookie.value}; Domain=$domain; Path=${cookie.path}$secureAttr"
    }
}
