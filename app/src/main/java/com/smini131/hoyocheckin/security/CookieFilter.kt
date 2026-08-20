package com.smini131.hoyocheckin.security

/** 원신 출석 요청에 필요할 수 있는 인증 쿠키 이름만 보존한다. */
object CookieFilter {
    private val allowedNames = setOf(
        "ltuid",
        "ltoken",
        "ltuid_v2",
        "ltoken_v2",
        "ltmid_v2",
        "account_id",
        "account_id_v2",
        "account_mid_v2",
        "cookie_token",
        "cookie_token_v2",
        "cookie_mid_v2"
    )

    fun extract(vararg cookieSources: String?): String? {
        val values = linkedMapOf<String, String>()
        cookieSources.filterNotNull().forEach { source ->
            source.split(';').forEach { rawPart ->
                val part = rawPart.trim()
                val separator = part.indexOf('=')
                if (separator <= 0) return@forEach
                val name = part.substring(0, separator).trim()
                val value = part.substring(separator + 1).trim()
                if (name in allowedNames && value.isNotEmpty()) values[name] = value
            }
        }
        if (!hasRequiredPair(values)) return null
        return values.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    private fun hasRequiredPair(values: Map<String, String>): Boolean {
        val legacy = values.containsKey("ltuid") && values.containsKey("ltoken")
        val modernLToken = values.containsKey("ltuid_v2") && values.containsKey("ltoken_v2")
        val modernCookieToken = values.containsKey("account_id_v2") && values.containsKey("cookie_token_v2")
        return legacy || modernLToken || modernCookieToken
    }
}
