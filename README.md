# Constraint Native Engine

Constraint Native Engine is a high-performance, type-safe Kotlin multi-platform library for solving complex combinatorial optimization problems, specifically tailored for constrained recommendations like outfit assembly, product bundles, and smart planning.

It integrates a deterministic solver (exact Depth-First Search and Bounded Beam Search) with a LLM-based generative ranking adapter using the Gemini API.

---

## Architecture Overview

The project is structured as a Gradle multi-module build:
- **`:engine-core`**: Core model declarations, types, inline value classes, interfaces for constraints, scorers, and Serializable trace events.
- **`:engine-solver`**: Exact DFS and Bounded Beam Search solvers, canonicalizer, and Jaccard-based diversity reranking.
- **`:engine-generative`**: Suspending orchestrator coordinating local constraints and remote LLM ranking with strict validation.
- **`:adapter-gemini`**: Strictly-typed JSON adapter mapping request schemas to the Gemini API contents model.
- **`:domain-fashion`**: A reusable domain layer demonstrating wardrobe matching with temperature, occasion, layering, and color harmony soft scorers.
- **`:benchmark`**: JMH benchmarks validating solver speed and node expansion metrics.
- **`:examples:wardrobe-cli`**: A runnable CLI exemplifying wardrobe solving and generative ranking.

---

## Core Concepts

### 1. Constraint Modeling
Combinatorial spaces are modeled by specifying:
- **`SlotRequirement`**: The target slots to be filled, with minimum and maximum item counts.
- **`Candidate`**: Available inventory items, tagged with eligible slots and domain metadata (`A`).
- **`HardConstraint`**: Predicates that discard invalid solutions early during the tree traversal.
- **`SoftScorer`**: Weighting functions assigning provisional scores to partial/complete states.

### 2. Search Mode Selection
The engine offers two search modes:
- **`SearchMode.EXACT`**: A backtracking branch-and-bound algorithm exploring the entire search space. It guarantees the absolute global optimum but scales exponentially.
- **`SearchMode.BOUNDED_BEAM`**: A level-by-level breadth-first beam search. At each level, it expands candidate successors, scores them, and retains only the top `beamWidth` states. Extremely scalable, but may skip global optima depending on beam width.

### 3. Diversity Reranking
Reranking balances candidate score quality against novelty using a greedy optimization function:
\[
\text{metric} = \text{normalizedQuality} - \text{diversityWeight} \times \max_{S_j \in \text{Selected}} \text{JaccardSimilarity}(S_i, S_j)
\]
Where Jaccard similarity is computed over the set of candidate IDs present in the outfits.

### 4. Generative Reranking & Validation
To add subjective intelligence, the engine supports external LLM rankers (`GenerativeRanker`). To prevent issues like hallucinated IDs, truncation, or prompt injection:
- Input summaries are serialized and isolated as escaped JSON payloads.
- The ranker output is validated strictly using `RankingValidator`:
  - Rejects unknown or duplicate solution IDs.
  - Rejects oversized explanations (> 2000 chars) or control characters.
  - Resolves subset rankings by deterministically appending omitted solutions in their local score order.

---

## Code Examples

### 1. Library Setup & Basic Solve

Here is how to set up the candidates, slots, constraints, and run the exact solver:

```kotlin
import io.github.constraintnative.core.*
import io.github.constraintnative.solver.DeterministicSolver

val solver = DeterministicSolver()

val candidates = listOf(
    Candidate(
        id = CandidateId("t-shirt"),
        attributes = "Casual T-Shirt",
        eligibleSlots = setOf(SlotId("top"))
    ),
    Candidate(
        id = CandidateId("jeans"),
        attributes = "Blue Jeans",
        eligibleSlots = setOf(SlotId("bottom"))
    )
)

val slots = listOf(
    SlotRequirement(SlotId("top"), min = 1, max = 1),
    SlotRequirement(SlotId("bottom"), min = 1, max = 1)
)

val request = SolveRequest(
    candidates = candidates,
    slots = slots,
    context = Unit,
    hardConstraints = emptyList(),
    softScorers = emptyList(),
    desiredSolutions = 3,
    searchMode = SearchMode.EXACT,
    searchBudget = SearchBudget(maxExpandedNodes = 1000L, maxDurationMillis = 5000L, beamWidth = 10),
    seed = 42L
)

val result = solver.solve(request)
result.solutions.forEach { solution ->
    println("Solution ${solution.id.value}:")
    solution.assignments.forEach { println("  ${it.slot.value} = ${it.candidate.id.value}") }
}
```

### 2. Custom Hard Constraint Implementation

```kotlin
import io.github.constraintnative.core.*

class SpecificMatchConstraint(
    val slotA: SlotId,
    val slotB: SlotId
) : HardConstraint<String, Unit> {
    
    override fun evaluate(assembly: PartialAssembly<String>, context: Unit): ConstraintVerdict {
        val valA = assembly.assignments.find { it.slot == slotA }?.candidate?.id?.value
        val valB = assembly.assignments.find { it.slot == slotB }?.candidate?.id?.value
        
        if (valA == null || valB == null) {
            return ConstraintVerdict.Undetermined
        }
        
        // Example logic: IDs must have matching suffixes
        return if (valA.takeLast(2) == valB.takeLast(2)) {
            ConstraintVerdict.Satisfied
        } else {
            ConstraintVerdict.Violated("suffix_mismatch", "Items did not match suffix")
        }
    }
}
```

---

## Building and Running Benchmarks

1. **Build and Test All Modules**:
   ```bash
   ./gradlew test apiCheck
   ```

2. **Run Wardrobe CLI (examples/wardrobe-cli)**:
   ```bash
   ./gradlew :examples:wardrobe-cli:run --args="--occasion=Formal --temperature=12.0 --diversityWeight=0.5"
   ```

3. **Run JMH Performance Benchmarks**:
   ```bash
   ./gradlew :benchmark:mainBenchmark
   ```
