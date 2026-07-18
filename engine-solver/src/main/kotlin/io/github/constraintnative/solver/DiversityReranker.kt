package io.github.constraintnative.solver

import io.github.constraintnative.core.CandidateId
import io.github.constraintnative.core.FeasibleSolution

public object DiversityReranker {
    public fun <A> rerank(
        solutions: List<FeasibleSolution<A>>,
        diversityWeight: Double
    ): List<FeasibleSolution<A>> {
        require(diversityWeight in 0.0..1.0) {
            "diversityWeight must be in 0.0..1.0, got: $diversityWeight"
        }

        if (solutions.size <= 1) {
            return solutions.toList()
        }

        val minScore = solutions.minOf { it.totalScore }
        val maxScore = solutions.maxOf { it.totalScore }
        val range = maxScore - minScore

        val solutionSets = solutions.associateWith { sol ->
            sol.assignments.map { it.candidate.id }.toSet()
        }

        fun jaccardSimilarity(s1: Set<CandidateId>, s2: Set<CandidateId>): Double {
            val intersectionSize = s1.count { it in s2 }
            val unionSize = s1.size + s2.size - intersectionSize
            if (unionSize == 0) return 0.0
            return intersectionSize.toDouble() / unionSize
        }

        val unselected = solutions.toMutableList()
        val selected = mutableListOf<FeasibleSolution<A>>()
        val selectedSets = mutableListOf<Set<CandidateId>>()

        while (unselected.isNotEmpty()) {
            var bestSolution: FeasibleSolution<A>? = null
            var bestMetric = Double.NEGATIVE_INFINITY
            var bestIndex = -1

            for (i in unselected.indices) {
                val sol = unselected[i]
                val solSet = solutionSets[sol] ?: emptySet()

                val quality = if (range == 0.0) 1.0 else (sol.totalScore - minScore) / range
                val maxSim = if (selectedSets.isEmpty()) {
                    0.0
                } else {
                    selectedSets.maxOf { jaccardSimilarity(solSet, it) }
                }

                val metric = quality - diversityWeight * maxSim

                if (metric > bestMetric) {
                    bestMetric = metric
                    bestSolution = sol
                    bestIndex = i
                }
            }

            if (bestSolution != null) {
                selected.add(bestSolution)
                selectedSets.add(solutionSets[bestSolution] ?: emptySet())
                unselected.removeAt(bestIndex)
            } else {
                break
            }
        }

        return selected
    }
}
