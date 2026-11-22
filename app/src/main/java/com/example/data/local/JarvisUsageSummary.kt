package com.example.data.local

data class UsageSummaryState(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalLoggedTokens: Int = 0,
    val exactTokenLogCount: Int = 0,
    val lastRateLimitInfo: String? = null,
    val lastModelId: String? = null,
    val thresholdWarningActive: Boolean = false
) {
    companion object {
        fun fromLogs(logs: List<AiUsageLogEntity>, warningLimit: Int): UsageSummaryState {
            val prompt = logs.sumOf { it.promptTokens }
            val completion = logs.sumOf { it.completionTokens }
            val total = logs.sumOf { it.totalTokens }
            return UsageSummaryState(
                totalRequests = logs.size,
                successfulRequests = logs.count { it.succeeded },
                failedRequests = logs.count { !it.succeeded },
                promptTokens = prompt,
                completionTokens = completion,
                totalLoggedTokens = total,
                exactTokenLogCount = logs.count { it.tokenCountsExact },
                lastRateLimitInfo = logs.firstOrNull { !it.rateLimitInfo.isNullOrBlank() }?.rateLimitInfo,
                lastModelId = logs.firstOrNull()?.modelId,
                thresholdWarningActive = total >= warningLimit
            )
        }
    }
}
