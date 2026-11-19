package com.example

import com.example.domain.CookieParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieParserTest {

    @Test
    fun parsesFacebookSemicolonCookieStringWithoutBreakingEqualsTokens() {
        val sample = "datr=bKX-alwSVVrUUMRV43okCIYn; sb=baX-auojU8ucFosdXFrufM4C; " +
            "dpr=3.049999952316284; c_user=61596622534624; oo=v1%7C3%3A1795073475; " +
            "locale=en_US; x-referer=eyJyIjoiL3Byb2ZpbGUucGhwP2lkPTYxNTk2NjIyNTM0NjI0In0%3D; " +
            "i_user=61596914991880; xs=37%3AyhR7a-d3KtLFWg%3A2%3A1795073472%3A-1%3A-1%3A%3AAc0oHcamt91MKvZhzKzZYYrVhH-cMMJqSk8vXGbMSQ; wd=1280x2351"

        val result = CookieParser.parse(sample)

        assertEquals("SEMICOLON", result.sourceFormat)
        assertEquals("61596622534624", result.fbUserId)
        assertEquals("61596914991880", result.iUserId)
        assertEquals(10, result.cookies.size)

        val referer = result.cookies.first { it.name == "x-referer" }
        assertTrue(referer.value.endsWith("%3D"))
        val wd = result.cookies.first { it.name == "wd" }
        assertEquals("1280x2351", wd.value)
    }

    @Test
    fun parsesJsonCookieArray() {
        val json = """
            [
              {"name":"c_user","value":"100088112233","domain":".facebook.com","path":"/","secure":true},
              {"name":"xs","value":"12%3Aabc%3D","domain":".facebook.com","path":"/","secure":true}
            ]
        """.trimIndent()

        val result = CookieParser.parse(json)

        assertEquals("JSON", result.sourceFormat)
        assertEquals("100088112233", result.fbUserId)
        assertEquals(2, result.cookies.size)
        assertTrue(result.normalizedCookieString.contains("c_user=100088112233"))
    }
}
