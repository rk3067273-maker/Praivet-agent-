package com.example.data.security

object PromptSecurityGuard {

    private val bidiAndZeroWidthRegex = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]")
    private val actionTagRegex = Regex("""\[ACTION:[^\]]*\]""", RegexOption.IGNORE_CASE)

    private val secretPatterns = listOf(
        Regex("""sk-[A-Za-z0-9_-]{12,}"""),
        Regex("""AIza[0-9A-Za-z_-]{20,}"""),
        Regex("""nvapi-[A-Za-z0-9_-]{12,}"""),
        Regex("""ghp_[A-Za-z0-9]{16,}"""),
        Regex("""\bBearer\s+[A-Za-z0-9._-]{12,}""", RegexOption.IGNORE_CASE),
        Regex("""-----BEGIN\s+[A-Z\s]*PRIVATE\s+KEY-----"""),
        Regex(
            """\b(?:otp|one[- ]time[- ]pass(?:code|word)?|pin|password|passphrase|secret|api[-_ ]?key|token)\b\s*(?:is|=|:)\s*[^\s,;]{3,}""",
            RegexOption.IGNORE_CASE
        )
    )

    private val injectionPatterns = listOf(
        Regex("""ignore\s+(?:all\s+|any\s+)?(?:previous|prior|above)\s+instructions""", RegexOption.IGNORE_CASE),
        Regex("""disregard\s+(?:all\s+|any\s+)?(?:previous|prior|system)\s+instructions""", RegexOption.IGNORE_CASE),
        Regex("""override\s+(?:system|safety|developer)\s+(?:prompt|rules|policy)""", RegexOption.IGNORE_CASE),
        Regex("""reveal\s+(?:your\s+)?(?:system|developer)\s+prompt""", RegexOption.IGNORE_CASE),
        Regex("""you\s+are\s+now\s+(?:DAN|unrestricted|jailbroken)""", RegexOption.IGNORE_CASE),
        Regex("""(?:</?system>|\[INST\]|<<SYS>>|<\|im_start\|>system)""", RegexOption.IGNORE_CASE)
    )

    fun sanitizeUntrustedText(raw: String, maxChars: Int = 4000): String {
        val noInvisible = raw.replace(bidiAndZeroWidthRegex, "")
        val noSpoofedActionTags = noInvisible.replace(actionTagRegex, "[stripped-action-tag]")
        return noSpoofedActionTags.trim().take(maxChars)
    }

    fun containsSensitiveSecret(text: String): Boolean {
        return secretPatterns.any { it.containsMatchIn(text) }
    }

    fun detectPromptInjectionMarkers(text: String): List<String> {
        val normalized = text.replace(bidiAndZeroWidthRegex, "")
        return injectionPatterns.mapNotNull { regex ->
            regex.find(normalized)?.value
        }
    }

    fun isSuspiciousForMemory(text: String): Boolean {
        return detectPromptInjectionMarkers(text).isNotEmpty() || actionTagRegex.containsMatchIn(text)
    }

    fun wrapUntrustedData(source: String, rawContent: String, maxChars: Int = 2500): String {
        val safeSource = source.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val cleaned = sanitizeUntrustedText(rawContent, maxChars)
            .replace("</untrusted_data>", "[/untrusted_data]")
        return "<untrusted_data source=\"$safeSource\">\n$cleaned\n</untrusted_data>"
    }
}
