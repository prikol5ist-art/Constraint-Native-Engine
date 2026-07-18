package io.github.constraintnative.solver

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DiversityRerankerTest {

    private val slotA = SlotId("slotA")
    private val slotB = SlotId("slotB")
    private val slotC = SlotId("slotC")

    private val c1 = Candidate(CandidateId("c1"), "c1", setOf(slotA))
    private val c2 = Candidate(CandidateId("c2"), "c2", setOf(slotB))
    private val c3 = Candidate(CandidateId("c3"), "c3", setOf(slotC))
    private val c4 = Candidate(CandidateId("c4"), "c4", setOf(slotC))
    private val c5 = Candidate(CandidateId("c5"), "c5", setOf(slotA))
    private val c6 = Candidate(CandidateId("c6"), "c6", setOf(slotB))
    private val c7 = Candidate(CandidateId("c7"), "c7", setOf(slotC))

    @Test
    fun `should validate diversity weight`() {
        assertThatThrownBy { DiversityReranker.rerank<String>(emptyList(), -0.1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DiversityReranker.rerank<String>(emptyList(), 1.1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should handle empty or single element lists`() {
        val empty = emptyList<FeasibleSolution<String>>()
        assertThat(DiversityReranker.rerank(empty, 0.5)).isEmpty()

        val single = listOf(
            FeasibleSolution(
                id = SolutionId("sol1"),
                assignments = listOf(Assignment(slotA, c1)),
                scoreComponents = emptyList(),
                totalScore = 10.0
            )
        )
        assertThat(DiversityReranker.rerank(single, 0.5)).containsExactlyElementsOf(single)
    }

    @Test
    fun `should respect zero weight`() {
        val sol1 = FeasibleSolution(
            id = SolutionId("sol1"),
            assignments = listOf(Assignment(slotA, c1)),
            scoreComponents = emptyList(),
            totalScore = 10.0
        )
        val sol2 = FeasibleSolution(
            id = SolutionId("sol2"),
            assignments = listOf(Assignment(slotA, c5)),
            scoreComponents = emptyList(),
            totalScore = 8.0
        )

        val reranked = DiversityReranker.rerank(listOf(sol2, sol1), 0.0)
        assertThat(reranked).containsExactly(sol1, sol2)
    }

    @Test
    fun `should let disjoint lower-score solution outrank similar higher-score solution under high diversity weight`() {
        // Sol1: c1, c2, c3 (score = 10.0)
        val sol1 = FeasibleSolution(
            id = SolutionId("sol1"),
            assignments = listOf(Assignment(slotA, c1), Assignment(slotB, c2), Assignment(slotC, c3)),
            scoreComponents = emptyList(),
            totalScore = 10.0
        )

        // Sol2: c1, c2, c4 (score = 9.0, Jaccard similarity to Sol1 = 2/4 = 0.5)
        val sol2 = FeasibleSolution(
            id = SolutionId("sol2"),
            assignments = listOf(Assignment(slotA, c1), Assignment(slotB, c2), Assignment(slotC, c4)),
            scoreComponents = emptyList(),
            totalScore = 9.0
        )

        // Sol3: c5, c6, c7 (score = 8.5, completely disjoint from Sol1 and Sol2, Jaccard similarity to Sol1 = 0)
        val sol3 = FeasibleSolution(
            id = SolutionId("sol3"),
            assignments = listOf(Assignment(slotA, c5), Assignment(slotB, c6), Assignment(slotC, c7)),
            scoreComponents = emptyList(),
            totalScore = 8.5
        )

        val input = listOf(sol1, sol2, sol3)

        // With low diversity weight (e.g. 0.1), Sol2 should outrank Sol3 because of its higher score
        val lowDiv = DiversityReranker.rerank(input, 0.1)
        assertThat(lowDiv).containsExactly(sol1, sol2, sol3)

        // With high diversity weight (e.g. 0.8), Sol3 should outrank Sol2 because Sol2 is too similar to Sol1
        val highDiv = DiversityReranker.rerank(input, 0.8)
        assertThat(highDiv).containsExactly(sol1, sol3, sol2)
    }
}
