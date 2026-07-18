package io.github.constraintnative.generative

import io.github.constraintnative.core.*
import io.github.constraintnative.solver.ConstraintSolver
import io.github.constraintnative.solver.DeterministicSolver
import io.github.constraintnative.solver.DiversityReranker
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

public class ConstraintNativeEngine(
    private val solver: ConstraintSolver = DeterministicSolver()
) {
    public suspend fun <A, C> generate(
        request: SolveRequest<A, C>,
        ranker: GenerativeRanker? = null,
        renderer: ContextRenderer<A, C>? = null,
        rankerTimeoutMillis: Long = 5000L,
        diversityWeight: Double = 0.0
    ): EngineResult<A> {
        val solveResult = solver.solve(request)
        if (solveResult.solutions.isEmpty()) {
            return EngineResult(
                solutions = emptyList(),
                termination = solveResult.termination,
                rankerStatus = RankerStatus.NOT_CONFIGURED
            )
        }

        val localSolutions = if (diversityWeight > 0.0) {
            DiversityReranker.rerank(solveResult.solutions, diversityWeight)
        } else {
            solveResult.solutions
        }

        if (ranker == null || renderer == null) {
            return EngineResult(
                solutions = localSolutions,
                termination = solveResult.termination,
                rankerStatus = RankerStatus.NOT_CONFIGURED
            )
        }

        val rankableSolutions = localSolutions.map { sol ->
            RankableSolution(sol.id, renderer.renderSolution(sol))
        }
        val contextSummary = renderer.renderContext(request.context)
        val rankingRequest = RankingRequest(rankableSolutions, contextSummary)

        val response = try {
            withTimeout(rankerTimeoutMillis) {
                ranker.rank(rankingRequest)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return EngineResult(
                solutions = localSolutions,
                termination = solveResult.termination,
                rankerStatus = RankerStatus.FELL_BACK,
                rankerError = "Ranker timed out after ${rankerTimeoutMillis}ms"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return EngineResult(
                solutions = localSolutions,
                termination = solveResult.termination,
                rankerStatus = RankerStatus.FELL_BACK,
                rankerError = t.message ?: t.toString()
            )
        }

        val validation = RankingValidator.validate(response, localSolutions)
        if (validation is ValidationResult.Invalid) {
            return EngineResult(
                solutions = localSolutions,
                termination = solveResult.termination,
                rankerStatus = RankerStatus.FELL_BACK,
                rankerError = "Validation failed: ${validation.code} - ${validation.message}"
            )
        }

        val validValidation = validation as ValidationResult.Valid
        val reorderedList = validValidation.reorder(localSolutions)

        val explanationMap = response.rankedSolutions.associate { it.id to it.explanation }
        val finalSolutions = reorderedList.map { sol ->
            sol.copy(explanation = explanationMap[sol.id])
        }

        return EngineResult(
            solutions = finalSolutions,
            termination = solveResult.termination,
            rankerStatus = RankerStatus.APPLIED
        )
    }
}
