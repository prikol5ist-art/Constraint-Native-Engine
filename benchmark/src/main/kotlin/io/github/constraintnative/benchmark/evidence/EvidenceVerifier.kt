package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.core.*
import io.github.constraintnative.solver.SolutionCanonicalizer

public object EvidenceVerifier {

    public fun <A, C> verifySolutions(
        solutions: List<FeasibleSolution<A>>,
        knownCandidates: List<Candidate<A>>,
        requirements: List<SlotRequirement>,
        hardConstraints: List<HardConstraint<A, C>>,
        context: C
    ): Int {
        val knownCandidateMap = knownCandidates.associateBy { it.id }
        var violationsCount = 0

        for (solution in solutions) {
            var isValid = true
            try {
                // 1. Candidate membership
                for (assignment in solution.assignments) {
                    val known = knownCandidateMap[assignment.candidate.id]
                    if (known == null) {
                        isValid = false
                        break
                    }
                }
                if (!isValid) {
                    violationsCount++
                    continue
                }

                // 2. Slot eligibility
                for (assignment in solution.assignments) {
                    val known = knownCandidateMap[assignment.candidate.id]!!
                    if (assignment.slot !in assignment.candidate.eligibleSlots || assignment.slot !in known.eligibleSlots) {
                        isValid = false
                        break
                    }
                }
                if (!isValid) {
                    violationsCount++
                    continue
                }

                // 3. Candidate uniqueness
                val candidateIds = solution.assignments.map { it.candidate.id }
                if (candidateIds.distinct().size != candidateIds.size) {
                    violationsCount++
                    continue
                }

                // 4. Slot requirements
                for (req in requirements) {
                    val count = solution.assignments.count { it.slot == req.slot }
                    if (count < req.min || count > req.max) {
                        isValid = false
                        break
                    }
                }
                if (!isValid) {
                    violationsCount++
                    continue
                }

                // 5. Hard constraints evaluation
                val assembly = PartialAssembly(solution.assignments, complete = true)
                for (constraint in hardConstraints) {
                    val verdict = constraint.evaluate(assembly, context)
                    if (verdict is ConstraintVerdict.Violated) {
                        isValid = false
                        break
                    }
                }
                if (!isValid) {
                    violationsCount++
                    continue
                }

                // 6. Canonical ID equality
                val canonicalId = SolutionCanonicalizer.canonicalize(solution.assignments)
                if (solution.id != canonicalId) {
                    violationsCount++
                    continue
                }

            } catch (e: Exception) {
                violationsCount++
            }
        }

        return violationsCount
    }

    public fun <A> unknownIdsAccepted(
        ranked: List<SolutionId>,
        knownSolutions: List<FeasibleSolution<A>>
    ): Int {
        val knownIds = knownSolutions.map { it.id }.toSet()
        return ranked.count { it !in knownIds }
    }
}
