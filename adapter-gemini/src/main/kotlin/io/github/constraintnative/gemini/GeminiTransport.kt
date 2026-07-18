package io.github.constraintnative.gemini

public fun interface GeminiTransport {
    public suspend fun generateContent(requestJson: String): String
}
