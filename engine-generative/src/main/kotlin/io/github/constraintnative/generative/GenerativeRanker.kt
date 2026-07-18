package io.github.constraintnative.generative

public fun interface GenerativeRanker {
    public suspend fun rank(request: RankingRequest): RankingResponse
}
