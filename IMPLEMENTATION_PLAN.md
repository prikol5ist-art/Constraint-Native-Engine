# Constraint-Native Generative Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an open-source Kotlin engine that creates and ranks combinations from a finite inventory while guaranteeing hard constraints locally, limiting an LLM to already feasible solution IDs, and exposing reproducible traces, benchmarks, and Android-compatible integration contracts.

**Architecture:** Use a pure Kotlin/JVM core with immutable contracts, a deterministic bounded solver, and a provider-neutral generative-ranking boundary. Domain modules define candidates, slots, constraints, and soft scores; an optional Gemini adapter ranks only solver-produced solution IDs. Smart Wardrobe integrates through a thin mapper and transport layer, while Android, Room, Supabase, Gemini credentials, and private app models remain outside the open-source core.

**Tech Stack:** Kotlin 2.1.x, JVM 17, Gradle Kotlin DSL, kotlinx.serialization, kotlinx.coroutines, JUnit 5, AssertJ, kotlinx-benchmark, Kover, Dokka, binary compatibility validation, GitHub Actions, Maven Central, Apache-2.0.

---

## 1. Product Contract

### Core problem

An LLM can judge subjective compatibility but cannot reliably guarantee that a recommendation:

- uses only objects present in a finite inventory;
- covers every required slot;
- respects availability, safety, ownership, and atomic-group rules;
- avoids forbidden combinations;
- remains inside a search, token, cost, or latency budget;
- returns identifiers that the host can resolve.

The engine moves these responsibilities out of the prompt and into deterministic code.

### v0.1 claim

> Given a finite inventory, typed slots, hard constraints, soft scorers, and a search budget, the engine returns only feasible assemblies. An optional generative model may rank and explain those assemblies, but cannot introduce candidates or invalidate solver guarantees.

### Invariants

1. Every selected candidate came from the caller's inventory.
2. Every candidate is assigned to an eligible slot.
3. A candidate appears at most once in an assembly.
4. Every returned assembly satisfies every hard constraint.
5. LLM output can only reorder known `SolutionId` values.
6. Invalid, duplicate, missing, or unknown LLM IDs trigger deterministic local fallback.
7. Equal inputs, configuration, and seed produce equal local results and traces.
8. Bounded search reports whether it was exhaustive; it never presents an interrupted search as proof of infeasibility.
9. Core modules perform no network, filesystem, Android, database, analytics, or secret access.
10. Candidate text and remote output are untrusted data.

### Non-goals for v0.1

- Train a model or embedding system.
- Build a general agent framework or document RAG framework.
- Let an LLM invent inventory objects.
- Support arbitrary SAT, SMT, or integer-programming syntax.
- Persist user data.
- Depend on Smart Wardrobe, Android, Room, or Supabase.
- Publish private traces, prompts, photos, or credentials.
- Claim global optimality after bounded search stops early.

### Release criteria

`0.1.0` is releasable only when:

- hard-constraint violation rate is zero across unit, randomized, replay, and adversarial tests;
- unknown ranker-ID acceptance rate is zero;
- deterministic replay passes on Linux, macOS, and Windows with JVM 17;
- a synthetic 500-item fashion inventory yields three feasible outfits within 250 ms p95 after JVM warm-up on the documented reference machine;
- bounded search returns an explicit budget-exhausted status;
- the CLI works without a cloud key;
- Gemini support uses an injected transport and stores no credentials;
- all public artifacts include sources, Javadocs, checksums, and signatures;
- API compatibility and dependency verification run in CI;
- Smart Wardrobe consumes released artifacts without adding Android dependencies to the engine.

---

## 2. Repository Placement

Copy this plan into a new sibling repository:

```text
/Users/abko/Downloads/
├── qwer/
└── constraint-native-engine/
```

Do not make the public project a nested Git repository inside `qwer/`.

Bootstrap commands:

```bash
mkdir -p /Users/abko/Downloads/constraint-native-engine
cd /Users/abko/Downloads/constraint-native-engine
git init
cp /Users/abko/Downloads/qwer/docs/superpowers/plans/2026-07-18-constraint-native-generative-engine.md IMPLEMENTATION_PLAN.md
```

Working Maven coordinates:

```text
group: io.github.constraintnative
artifacts: engine-core, engine-solver, engine-generative, adapter-gemini, domain-fashion
version: 0.1.0-SNAPSHOT
```

Verify the public name, GitHub organization, and Maven namespace before the first publication.

---

## 3. Architecture

### Modules

```text
constraint-native-engine/
├── engine-core/                 # IDs, inventory, constraints, scores, results, trace
├── engine-solver/               # deterministic exact and bounded search
├── engine-generative/           # ranker SPI, validation, fallback orchestration
├── adapter-gemini/              # strict Gemini JSON protocol over injected transport
├── domain-fashion/              # portable fashion ontology and policies
├── benchmark/                   # synthetic data, baselines, reports
├── examples/wardrobe-cli/       # local runnable demonstration
├── docs/
├── build-logic/
├── gradle/
├── .github/
├── IMPLEMENTATION_PLAN.md
├── README.md
├── LICENSE
├── SECURITY.md
├── CONTRIBUTING.md
├── GOVERNANCE.md
└── CITATION.cff
```

### Runtime flow

```text
Inventory + Request + Domain Context
                 |
                 v
       Normalize and validate IDs
                 |
                 v
       Apply deterministic pruning
                 |
                 v
     Exact or bounded feasible search
                 |
      no solution + exhaustive ------> INFEASIBLE + trace
      no solution + interrupted -----> BUDGET_EXHAUSTED + trace
                 |
                 v
       Locally scored solutions
                 |
         no remote ranker ------------> deterministic local order
                 |
                 v
      Rank known solution IDs only
                 |
                 v
       Validate remote response
                 |
       valid                    invalid/error
         |                           |
         v                           v
 remote order              deterministic local fallback
         |                           |
         +-------------+-------------+
                       v
          EngineResult + provenance
```

### Trust boundaries

- Trusted: host IDs after validation, typed attributes, registered constraints, solver and validator output.
- Untrusted: display text, user notes, remote explanations, network payloads, remote ordering.
- Secret-bearing: host transport only. The library never reads an API key.
- Private: inventory and traces. The engine persists or transmits nothing by itself.

### Public API anchor

```kotlin
@JvmInline value class CandidateId(val value: String)
@JvmInline value class SlotId(val value: String)
@JvmInline value class GroupId(val value: String)
@JvmInline value class SolutionId(val value: String)

data class Candidate<A>(
    val id: CandidateId,
    val attributes: A,
    val eligibleSlots: Set<SlotId>,
    val groupId: GroupId? = null
)

data class SlotRequirement(val slot: SlotId, val min: Int, val max: Int)
data class Assignment<A>(val slot: SlotId, val candidate: Candidate<A>)
data class PartialAssembly<A>(
    val assignments: List<Assignment<A>>,
    val complete: Boolean
)

sealed interface ConstraintVerdict {
    data object Satisfied : ConstraintVerdict
    data object Undetermined : ConstraintVerdict
    data class Violated(val code: String, val message: String) : ConstraintVerdict
}

fun interface HardConstraint<A, C> {
    fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict
}

data class ScoreComponent(val name: String, val value: Double)

fun interface SoftScorer<A, C> {
    fun score(assembly: PartialAssembly<A>, context: C): ScoreComponent
}

data class SearchBudget(
    val maxExpandedNodes: Long,
    val maxDurationMillis: Long,
    val beamWidth: Int
)

enum class SearchMode { EXACT, BOUNDED_BEAM }

data class SolveRequest<A, C>(
    val candidates: List<Candidate<A>>,
    val slots: List<SlotRequirement>,
    val context: C,
    val hardConstraints: List<HardConstraint<A, C>>,
    val softScorers: List<SoftScorer<A, C>>,
    val desiredSolutions: Int,
    val searchMode: SearchMode,
    val searchBudget: SearchBudget,
    val seed: Long
)

data class FeasibleSolution<A>(
    val id: SolutionId,
    val assignments: List<Assignment<A>>,
    val scoreComponents: List<ScoreComponent>,
    val totalScore: Double
)

enum class SearchTermination {
    EXHAUSTIVE,
    SOLUTION_LIMIT_REACHED,
    NODE_BUDGET_EXHAUSTED,
    TIME_BUDGET_EXHAUSTED,
    INFEASIBLE
}

data class SolveResult<A>(
    val solutions: List<FeasibleSolution<A>>,
    val termination: SearchTermination,
    val expandedNodes: Long,
    val trace: List<TraceEvent>
)
```

---

## 4. File Responsibilities

### `engine-core`

- `Ids.kt`: validated inline identifiers.
- `Candidate.kt`: candidates, slots, assignments, partial assemblies.
- `Constraint.kt`: hard-constraint contracts and verdicts.
- `Scoring.kt`: soft-score contracts and validation.
- `Search.kt`: requests, budgets, results, termination.
- `Trace.kt`: structured serializable provenance.
- `BuiltInConstraints.kt`: predicate, forbidden-pair, required-together, and atomic-group rules.

### `engine-solver`

- `ConstraintSolver.kt`: solver interface.
- `SearchState.kt`: internal immutable search state.
- `DeterministicSolver.kt`: exact DFS and bounded beam search.
- `SolutionCanonicalizer.kt`: stable IDs and tie-breaking.
- `DiversityReranker.kt`: optional diversity ordering.

### `engine-generative`

- `GenerativeRanker.kt`: provider-neutral suspend SPI.
- `RankingModels.kt`: request, response, failure types.
- `RankingValidator.kt`: known-ID and cardinality validation.
- `ContextRenderer.kt`: safe structured rendering.
- `ConstraintNativeEngine.kt`: solve, rank, validate, fallback.
- `EngineResult.kt`: output and ranker status.

### `adapter-gemini`

- `GeminiTransport.kt`: injected JSON transport.
- `GeminiRankingProtocol.kt`: strict serializable DTOs.
- `GeminiPromptFactory.kt`: fixed instructions and separated data.
- `GeminiGenerativeRanker.kt`: call, parse, timeout, typed failure.

### `domain-fashion`

- `FashionModels.kt`: portable attributes and context.
- `FashionSlots.kt`: separates and dress templates.
- `FashionConstraints.kt`: availability, weather, formality, atomic sets.
- `FashionScorers.kt`: style, color, recency, preference.
- `FashionRenderer.kt`: compact ranking summaries.
- `FashionPolicy.kt`: reusable defaults.

---

## 5. Standalone Implementation Tasks

### Task 1: Bootstrap the multi-module build

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `.gitignore`
- Create: all module directories from Section 3

- [ ] Initialize Gradle:

```bash
gradle wrapper --gradle-version 8.12.1 --distribution-type all
```

Expected: wrapper scripts and `gradle/wrapper/` exist.

- [ ] Write `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "constraint-native-engine"
include(
    ":engine-core",
    ":engine-solver",
    ":engine-generative",
    ":adapter-gemini",
    ":domain-fashion",
    ":benchmark",
    ":examples:wardrobe-cli"
)
```

- [ ] Pin Kotlin, coroutines, serialization, JUnit, AssertJ, Kover, Dokka, benchmark, and binary-compatibility plugins in `gradle/libs.versions.toml`; use JVM toolchain 17 and explicit API mode for public modules.

- [ ] Configure root group/version and API validation:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.binary.compatibility)
    alias(libs.plugins.benchmark) apply false
}

group = "io.github.constraintnative"
version = "0.1.0-SNAPSHOT"

allprojects {
    group = rootProject.group
    version = rootProject.version
}

apiValidation {
    ignoredProjects.addAll(listOf("benchmark", "wardrobe-cli"))
}
```

- [ ] Verify:

```bash
./gradlew projects test
```

Expected: seven modules listed and a successful empty build.

- [ ] Commit:

```bash
git add .
git commit -m "build: bootstrap constraint-native engine"
```

### Task 2: Add IDs and inventory models with TDD

**Files:**
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/Ids.kt`
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/Candidate.kt`
- Test: `engine-core/src/test/kotlin/io/github/constraintnative/core/IdsTest.kt`
- Test: `engine-core/src/test/kotlin/io/github/constraintnative/core/CandidateTest.kt`

- [ ] Write failing tests for printable, trimmed, non-blank IDs of at most 128 characters; reject control characters.
- [ ] Run `./gradlew :engine-core:test --tests '*IdsTest'`; expect compilation failure.
- [ ] Implement `CandidateId`, `SlotId`, `GroupId`, and `SolutionId` through one private validator.
- [ ] Write tests requiring at least one eligible slot, `0 <= min <= max`, unique candidate IDs, and no duplicate assignment.
- [ ] Run `./gradlew :engine-core:test`; expect all tests to pass.
- [ ] Commit `feat(core): add validated inventory models`.

### Task 3: Add constraints, scoring, search, and trace contracts

**Files:**
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/Constraint.kt`
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/Scoring.kt`
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/Search.kt`
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/Trace.kt`
- Test: `engine-core/src/test/kotlin/io/github/constraintnative/core/SearchContractsTest.kt`

- [ ] Write failing tests for: `desiredSolutions` in `1..100`; positive budgets; `beamWidth >= desiredSolutions`; unique IDs; finite scores; constraint codes matching `[a-z0-9_.-]{1,80}`.
- [ ] Implement the contracts from Section 3.
- [ ] Implement trace variants `CandidateRejected`, `PartialAssemblyPruned`, `SolutionAccepted`, and `SearchStopped`, each with a monotonic `sequence`.
- [ ] Sanitize trace messages to one line and at most 512 characters.
- [ ] Run `./gradlew :engine-core:test apiDump`; review the generated API baseline.
- [ ] Commit `feat(core): define constraints search and trace contracts`.

### Task 4: Add reusable hard constraints

**Files:**
- Create: `engine-core/src/main/kotlin/io/github/constraintnative/core/BuiltInConstraints.kt`
- Test: `engine-core/src/test/kotlin/io/github/constraintnative/core/BuiltInConstraintsTest.kt`

- [ ] Write failing tests for `AttributePredicateConstraint`, `ForbiddenPairConstraint`, `RequiredTogetherConstraint`, `AtomicGroupConstraint`, and `AllowedCombinationConstraint`.
- [ ] Require partial rules to return `Undetermined` when the assembly lacks enough information.
- [ ] Implement constraints without mutable global state or host framework types.
- [ ] Test empty groups, unavailable group members, unknown IDs, duplicate members, and hostile multiline messages.
- [ ] Run `./gradlew :engine-core:test` and commit `feat(core): add reusable hard constraints`.

### Task 5: Add stable solution identities

**Files:**
- Create: `engine-solver/src/main/kotlin/io/github/constraintnative/solver/SolutionCanonicalizer.kt`
- Test: `engine-solver/src/test/kotlin/io/github/constraintnative/solver/SolutionCanonicalizerTest.kt`

- [ ] Depend on `engine-core` using `api(project(":engine-core"))`.
- [ ] Write tests proving assignment order does not change the ID and slot changes do.
- [ ] Canonicalize as sorted `slot-id=candidate-id\n` records.
- [ ] Hash with JDK SHA-256 and use the first 16 digest bytes as lowercase hexadecimal.
- [ ] Pin one digest test vector and run it on every CI OS.
- [ ] Run `./gradlew :engine-solver:test` and commit `feat(solver): add stable solution identities`.

### Task 6: Implement exact deterministic search

**Files:**
- Create: `engine-solver/src/main/kotlin/io/github/constraintnative/solver/ConstraintSolver.kt`
- Create: `engine-solver/src/main/kotlin/io/github/constraintnative/solver/SearchState.kt`
- Create: `engine-solver/src/main/kotlin/io/github/constraintnative/solver/DeterministicSolver.kt`
- Test: `engine-solver/src/test/kotlin/io/github/constraintnative/solver/ExactSolverTest.kt`

- [ ] Write a failing test with two tops, one bottom, one pair of shoes, and two expected solutions.
- [ ] Define `ConstraintSolver.solve(request): SolveResult`.
- [ ] Implement depth-first branch-and-bound: stable slot order, stable candidate order, uniqueness, constraint check after every assignment, complete recheck, score sum in scorer order, canonical-ID tie-break.
- [ ] Return `INFEASIBLE` only after exhaustive search.
- [ ] Test missing slots, forbidden pairs, atomic groups, no eligible candidates, and max cardinality.
- [ ] Shuffle input 100 times and require identical IDs, ordering, scores, and traces.
- [ ] Run tests and commit `feat(solver): add deterministic exact search`.

### Task 7: Implement bounded beam search

**Files:**
- Modify: `engine-core/src/main/kotlin/io/github/constraintnative/core/Search.kt`
- Modify: `engine-solver/src/main/kotlin/io/github/constraintnative/solver/DeterministicSolver.kt`
- Test: `engine-solver/src/test/kotlin/io/github/constraintnative/solver/BoundedSolverTest.kt`

- [ ] Add `SearchMode.EXACT` and `SearchMode.BOUNDED_BEAM`.
- [ ] Write a node-budget test that expects `NODE_BUDGET_EXHAUSTED`, never `INFEASIBLE`.
- [ ] Implement deterministic beam retention by provisional score and candidate signature.
- [ ] Inject `MonotonicClock`; test time budgets with a fake clock and no sleeps.
- [ ] Ensure all partial and complete outputs still satisfy hard constraints.
- [ ] Run `./gradlew :engine-core:test :engine-solver:test apiCheck`; refresh API dumps only after reviewing changes.
- [ ] Commit `feat(solver): add bounded beam search`.

### Task 8: Add diversity reranking

**Files:**
- Create: `engine-solver/src/main/kotlin/io/github/constraintnative/solver/DiversityReranker.kt`
- Test: `engine-solver/src/test/kotlin/io/github/constraintnative/solver/DiversityRerankerTest.kt`

- [ ] Write tests where a slightly lower-scored disjoint solution outranks a duplicate-like solution when diversity is enabled.
- [ ] Implement `normalizedQuality - diversityWeight * maxJaccardSimilarityToSelected`.
- [ ] Validate weight in `0.0..1.0`; keep original scores unchanged.
- [ ] Test zero weight, empty input, and stable ties.
- [ ] Commit `feat(solver): add diversity reranking`.

### Task 9: Add the ranker SPI and validator

**Files:**
- Create: `engine-generative/src/main/kotlin/io/github/constraintnative/generative/GenerativeRanker.kt`
- Create: `engine-generative/src/main/kotlin/io/github/constraintnative/generative/RankingModels.kt`
- Create: `engine-generative/src/main/kotlin/io/github/constraintnative/generative/ContextRenderer.kt`
- Create: `engine-generative/src/main/kotlin/io/github/constraintnative/generative/RankingValidator.kt`
- Test: `engine-generative/src/test/kotlin/io/github/constraintnative/generative/RankingValidatorTest.kt`

- [ ] Define `RankableSolution`, `RankingRequest`, `RankedSolution`, `RankingResponse`, `GenerativeRanker`, and `ContextRenderer`.
- [ ] Write failing tests rejecting unknown IDs, duplicate IDs, excessive result counts, empty rankings, oversized explanations, and forbidden control characters.
- [ ] Accept a known subset and append omitted known solutions in deterministic local order.
- [ ] Return typed `Valid` or `Invalid(code, message)` validation outcomes.
- [ ] Run tests and commit `feat(generative): define ranker boundary and validation`.

### Task 10: Orchestrate solving and generative ranking

**Files:**
- Create: `engine-generative/src/main/kotlin/io/github/constraintnative/generative/EngineResult.kt`
- Create: `engine-generative/src/main/kotlin/io/github/constraintnative/generative/ConstraintNativeEngine.kt`
- Test: `engine-generative/src/test/kotlin/io/github/constraintnative/generative/ConstraintNativeEngineTest.kt`

- [ ] Define `RankerStatus.NotConfigured`, `Applied`, and `FellBack`.
- [ ] Test local-only order, valid remote order, unknown ID, exception, timeout, infeasible solve, explanation attachment, and immutable assignments.
- [ ] Implement a suspending `generate` method with a configurable ranker timeout.
- [ ] Re-throw parent `CancellationException`; convert transport, parse, timeout, and validation failures into deterministic fallback.
- [ ] Never call the ranker when there are no feasible solutions.
- [ ] Run tests and commit `feat(generative): orchestrate constrained generation`.

### Task 11: Add the Gemini adapter

**Files:**
- Create: `adapter-gemini/src/main/kotlin/io/github/constraintnative/gemini/GeminiTransport.kt`
- Create: `adapter-gemini/src/main/kotlin/io/github/constraintnative/gemini/GeminiRankingProtocol.kt`
- Create: `adapter-gemini/src/main/kotlin/io/github/constraintnative/gemini/GeminiPromptFactory.kt`
- Create: `adapter-gemini/src/main/kotlin/io/github/constraintnative/gemini/GeminiGenerativeRanker.kt`
- Test: `adapter-gemini/src/test/kotlin/io/github/constraintnative/gemini/GeminiGenerativeRankerTest.kt`

- [ ] Define a credential-free interface:

```kotlin
fun interface GeminiTransport {
    suspend fun generateContent(requestJson: String): String
}
```

- [ ] Use strict kotlinx.serialization JSON with unknown keys rejected and leniency disabled.
- [ ] Require response shape `{ "ranked": [{"solution_id":"...","explanation":"..."}] }`.
- [ ] Separate fixed instructions from a JSON data payload; escape every candidate summary.
- [ ] Request `application/json` and a response schema when the host transport supports it.
- [ ] Test prompt injection text, malformed JSON, markdown fences, unknown fields, duplicates, empty output, 100,000-character output, transport failure, and cancellation.
- [ ] Verify the module never reads an environment variable, API key, file, or Android context.
- [ ] Commit `feat(gemini): add injected ranking adapter`.

### Task 12: Implement the reusable fashion module

**Files:**
- Create: `domain-fashion/src/main/kotlin/io/github/constraintnative/fashion/FashionModels.kt`
- Create: `domain-fashion/src/main/kotlin/io/github/constraintnative/fashion/FashionSlots.kt`
- Create: `domain-fashion/src/main/kotlin/io/github/constraintnative/fashion/FashionConstraints.kt`
- Create: `domain-fashion/src/main/kotlin/io/github/constraintnative/fashion/FashionScorers.kt`
- Create: `domain-fashion/src/main/kotlin/io/github/constraintnative/fashion/FashionRenderer.kt`
- Create: `domain-fashion/src/main/kotlin/io/github/constraintnative/fashion/FashionPolicy.kt`
- Test: `domain-fashion/src/test/kotlin/io/github/constraintnative/fashion/FashionPolicyTest.kt`

- [ ] Define portable string-backed attributes: category, type, color, material, silhouette, formality, warmth, breathability, normalized style clusters, availability, laundry, wear history.
- [ ] Define context: temperature, precipitation, wind, occasion, optional target-formality range, preferred clusters, avoided colors, current time.
- [ ] Validate scores and measurements; preserve unknown values as null rather than inventing them.
- [ ] Add templates:

```text
SEPARATES: top=1, bottom=1, shoes=1, outerwear=0..1, accessory=0..2
DRESS: full_body=1, shoes=1, outerwear=0..1, accessory=0..2
```

- [ ] Implement availability, laundry, rain, snow, heat, cold, formality, locked-set, and category-coverage constraints.
- [ ] Relax only soft preferences during fallback; never relax ownership, availability, laundry, or atomic groups.
- [ ] Add normalized scorers for style, color, formality, recency, underuse, and weather comfort; validate weights sum to one.
- [ ] Render only ranking-relevant structured fields; exclude paths, images, database details, and free-form notes by default.
- [ ] Reproduce Smart Wardrobe `WeatherFilterTest` cases as independent fixtures.
- [ ] Commit `feat(fashion): add portable outfit policies`.

### Task 13: Add a runnable CLI

**Files:**
- Create: `examples/wardrobe-cli/src/main/kotlin/io/github/constraintnative/examples/wardrobe/Main.kt`
- Create: `examples/wardrobe-cli/src/main/resources/sample-wardrobe.json`
- Test: `examples/wardrobe-cli/src/test/kotlin/io/github/constraintnative/examples/wardrobe/WardrobeCliTest.kt`

- [ ] Create a synthetic inventory with tops, bottoms, dresses, shoes, outerwear, accessories, a laundry item, and an atomic set.
- [ ] Support `--weather`, `--temperature`, `--template`, `--solutions`, `--max-nodes`, and `--trace`.
- [ ] Exit `0` on success, `2` on invalid arguments, and `3` on proven infeasibility.
- [ ] Pin deterministic JSON golden outputs for clear, rain, snow, and infeasible fixtures.
- [ ] Run:

```bash
./gradlew :examples:wardrobe-cli:run --args='--weather rain --temperature 8 --template separates --solutions 3 --trace'
```

Expected: no sandals, only feasible IDs, stable IDs, and trace reasons.

- [ ] Commit `feat(example): add deterministic wardrobe CLI`.

### Task 14: Add randomized and adversarial verification

**Files:**
- Create: `engine-solver/src/test/kotlin/io/github/constraintnative/solver/RandomizedInvariantTest.kt`
- Create: `engine-generative/src/test/kotlin/io/github/constraintnative/generative/AdversarialRankerTest.kt`
- Create: `domain-fashion/src/test/kotlin/io/github/constraintnative/fashion/FashionInvariantTest.kt`

- [ ] Generate 1,000 seeded inventories with 1–100 candidates, 1–6 slots, random eligibility, forbidden pairs, and groups.
- [ ] Independently recalculate inventory membership, eligibility, uniqueness, cardinality, constraints, and solution IDs.
- [ ] Fuzz ranker output with unknown IDs, duplicates, missing fields, control text, truncated JSON, wrappers, and oversized payloads.
- [ ] Prove a soft score can be zero without invalidating an assembly.
- [ ] Set Kover minimums: 90% for core/solver/generative, 85% for fashion/Gemini adapter.
- [ ] Run `./gradlew test koverVerify apiCheck`.
- [ ] Commit `test: enforce engine invariants and adversarial fallback`.

### Task 15: Add reproducible benchmarks

**Files:**
- Create: `benchmark/src/main/kotlin/io/github/constraintnative/benchmark/SyntheticWardrobe.kt`
- Create: `benchmark/src/main/kotlin/io/github/constraintnative/benchmark/SolverBenchmark.kt`
- Create: `benchmark/src/main/kotlin/io/github/constraintnative/benchmark/Baselines.kt`
- Create: `benchmark/src/main/kotlin/io/github/constraintnative/benchmark/BenchmarkReport.kt`
- Test: `benchmark/src/test/kotlin/io/github/constraintnative/benchmark/BenchmarkAcceptanceTest.kt`
- Create: `docs/benchmark-methodology.md`

- [ ] Benchmark inventories of 25, 100, 500, and 1,000 items; 1, 3, and 10 solutions; multiple beam widths and weather contexts.
- [ ] Compare input-order `take(K)`, seeded category-balanced random, style-only top-K, score-only top-K, exact search where tractable, and bounded engine search.
- [ ] Record hard violations, feasible count, slot completeness, nodes, termination, p50/p95/p99 duration, rendered token estimate, exact-search regret, and pairwise diversity.
- [ ] Keep correctness acceptance tests separate from machine-sensitive timing thresholds.
- [ ] Run `./gradlew :benchmark:benchmark`; emit JSON and Markdown reports.
- [ ] Commit `bench: add finite-inventory solver baselines`.

### Task 16: Add open-source documentation and governance

**Files:**
- Create: `README.md`
- Create: `docs/architecture.md`
- Create: `docs/constraint-model.md`
- Create: `docs/generative-ranking.md`
- Create: `docs/android-integration.md`
- Create: `docs/privacy-and-threat-model.md`
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Create: `GOVERNANCE.md`
- Create: `CODE_OF_CONDUCT.md`
- Create: `LICENSE`
- Create: `CITATION.cff`

- [ ] Use canonical Apache-2.0 text and contributor ownership.
- [ ] Document guarantees, modules, Kotlin example, determinism, bounded semantics, benchmarks, Android compatibility, privacy, experimental maturity, limitations, and non-goals.
- [ ] Threat-model prompt injection, malicious output, unknown IDs, payload size, credentials, trace privacy, combinatorial exhaustion, dependency compromise, and log redaction.
- [ ] Document complete extension examples for a domain, constraint, scorer, transport, and host mapper.
- [ ] Require tests, API review, evidence for performance claims, DCO sign-off, and no private user datasets.
- [ ] Run `./gradlew dokkaGenerate test apiCheck`; expect no unresolved public references.
- [ ] Commit `docs: document architecture guarantees and governance`.

### Task 17: Add CI, integrity, and publication

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/benchmark.yml`
- Create: `.github/workflows/release.yml`
- Create: `.github/dependabot.yml`
- Create: `.github/ISSUE_TEMPLATE/bug.yml`
- Create: `.github/ISSUE_TEMPLATE/feature.yml`
- Create: `.github/pull_request_template.md`
- Create: `gradle/verification-metadata.xml`

- [ ] Run tests, coverage, and API checks on Ubuntu, macOS, and Windows with Temurin 17.
- [ ] Pin GitHub Actions by full commit SHA, use read-only default permissions, validate the Gradle wrapper, and perform dependency review.
- [ ] Generate SHA-256 dependency verification metadata and manually review newly resolved repositories.
- [ ] Publish only the five library modules with POM metadata, sources, Javadocs, checksums, signatures, and SBOM.
- [ ] Read signing and Maven credentials only from CI secrets or local Gradle properties.
- [ ] Require tag/version equality, all checks, staging publication, checksums, SBOM, benchmark snapshot, and manual approval before Maven Central release.
- [ ] Run `./gradlew publishToMavenLocal`; verify all five artifacts under `~/.m2/repository/io/github/constraintnative/`.
- [ ] Commit `ci: add verified build and release pipelines`.

Do not push, create a public GitHub repository, publish artifacts, or create a release without explicit owner approval.

---

## 6. Smart Wardrobe Integration

Execute this section in `/Users/abko/Downloads/qwer` only after Tasks 1–17 pass.

### Task 18: Add local and released dependency modes

**Files:**
- Modify: `/Users/abko/Downloads/qwer/settings.gradle.kts`
- Modify: `/Users/abko/Downloads/qwer/app/build.gradle.kts`
- Modify locally only: `/Users/abko/Downloads/qwer/local.properties`

- [ ] Read an optional `constraintNativeEnginePath` from untracked local configuration and use `includeBuild(path)` for development.
- [ ] Otherwise use released dependencies:

```kotlin
implementation("io.github.constraintnative:engine-generative:0.1.0")
implementation("io.github.constraintnative:adapter-gemini:0.1.0")
implementation("io.github.constraintnative:domain-fashion:0.1.0")
```

- [ ] Never commit an absolute local path.
- [ ] Run `./gradlew :app:compileDebugKotlin`; expect success before behavior changes.

### Task 19: Add a lossless app mapper

**Files:**
- Create: `app/src/main/java/com/smartwardrobe/domain/outfitengine/SmartWardrobeFashionMapper.kt`
- Test: `app/src/test/java/com/smartwardrobe/domain/outfitengine/SmartWardrobeFashionMapperTest.kt`

- [ ] Test stable ID, category, type, color, material, silhouette, formality, warmth, breathability, style scores, laundry, wear history, favorites, wardrobe set, and locked state.
- [ ] Preserve missing data as null or empty instead of fabricating it.
- [ ] Use explicit enum mappings, never `toString()` as a data contract.
- [ ] Exclude image paths, URIs, cutout paths, label images, filesystem details, and free-form notes from default ranker summaries.
- [ ] Run focused mapper tests.

### Task 20: Bind app constraints

**Files:**
- Create: `app/src/main/java/com/smartwardrobe/domain/outfitengine/SmartWardrobeConstraints.kt`
- Test: `app/src/test/java/com/smartwardrobe/domain/outfitengine/SmartWardrobeConstraintsTest.kt`
- Reference: `app/src/test/java/com/smartwardrobe/data/remote/WeatherFilterTest.kt`

- [ ] Reuse current weather fixtures as behavioral contract tests.
- [ ] Enforce `isLockedToSet`: selecting one locked member requires every available locked member in compatible slots.
- [ ] Reject a locked set that cannot fit the chosen template.
- [ ] Prove bounded search, style relaxation, no network, and ranker failure never restore laundry, weather-hard-invalid, or split-set items.
- [ ] Run both old and new weather suites.

### Task 21: Connect the existing Gemini proxy

**Files:**
- Create: `app/src/main/java/com/smartwardrobe/domain/outfitengine/SmartWardrobeRankerTransport.kt`
- Test: `app/src/test/java/com/smartwardrobe/domain/outfitengine/SmartWardrobeRankerTransportTest.kt`

- [ ] Implement `GeminiTransport` through the existing authenticated Supabase proxy.
- [ ] Verify request model, JSON mode, timeout, authentication, and absence of API keys in logs.
- [ ] Test HTTP 401, 429, 500, timeout, malformed response, and cancellation.
- [ ] Fall back locally for recoverable failures and propagate caller cancellation.
- [ ] Do not remove legacy key paths until Task 23 proves every caller migrated.

### Task 22: Create one mandatory generation gateway

**Files:**
- Create: `app/src/main/java/com/smartwardrobe/domain/outfitengine/OutfitGenerationGateway.kt`
- Test: `app/src/test/java/com/smartwardrobe/domain/outfitengine/OutfitGenerationGatewayTest.kt`

- [ ] Define one app request containing inventory, weather, occasion, desired count, preferences, source flow, and search budget.
- [ ] Solve dress and separates templates independently when both are feasible.
- [ ] Merge feasible solutions, apply local diversity, then let Gemini rank known IDs.
- [ ] Resolve every selected ID through the original inventory map.
- [ ] Map results to existing `OutfitSuggestionResult` without changing UI contracts initially.
- [ ] Test local-only, valid ranking, invalid ranking, offline, infeasible, dress-only, separates-only, locked-set, laundry, weather, and diversity cases.

### Task 23: Migrate all generation flows and block bypasses

**Files:**
- Modify: `app/src/main/java/com/smartwardrobe/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/smartwardrobe/ui/screens/freshlooks/FreshLookViewModel.kt`
- Modify: `app/src/main/java/com/smartwardrobe/data/remote/InspirationMatcher.kt`
- Modify: `app/src/main/java/com/smartwardrobe/worker/DailyOutfitWorker.kt`
- Modify: `app/src/main/java/com/smartwardrobe/data/remote/TravelCapsuleAiService.kt`
- Modify: `app/src/main/java/com/smartwardrobe/data/remote/OutfitAiService.kt`
- Create: `app/src/test/java/com/smartwardrobe/domain/outfitengine/GenerationBypassArchitectureTest.kt`

- [ ] Run Home in shadow mode first: preserve current output, compute local engine output without a second Gemini call, and compare redacted metrics.
- [ ] Require 100 replay cases, zero hard violations, and no complete-outfit-rate regression before promotion.
- [ ] Migrate Home, Fresh Looks, Inspiration, Daily Worker, and Travel separately.
- [ ] Add an architecture test that rejects direct `generateOutfitSuggestions(` calls outside the gateway/legacy adapter.
- [ ] Remove duplicate weather/style routing only after all callers migrate.
- [ ] Remove obsolete Gemini-key constructor dependencies and Android `BuildConfig` key if no verified client path uses it.
- [ ] Update `AGENTS.md` with the gateway and correct current database/model facts.
- [ ] Run:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Expected: all tasks pass and no bypass remains.

### Task 24: Add privacy-safe traces and a case study

**Files:**
- Create: `app/src/main/java/com/smartwardrobe/domain/outfitengine/EngineTraceLogger.kt`
- Test: `app/src/test/java/com/smartwardrobe/domain/outfitengine/EngineTraceLoggerTest.kt`
- Create in public repo: `docs/smart-wardrobe-case-study.md`

- [ ] Log only hashed IDs, constraint codes, slot counts, termination, node count, duration, ranker status, and token counts.
- [ ] Exclude images, paths, notes, prompts, explanations, profile text, location, and exact wardrobe metadata.
- [ ] Keep the public library telemetry-free; remote app telemetry requires existing consent and privacy policy.
- [ ] Test redaction using paths, email-like values, Ukrainian and English notes, and raw IDs.
- [ ] Publish only synthetic and aggregated inventory-size buckets, token reduction, latency, completeness, and violation rates.

---

## 7. Verification

Standalone repository:

```bash
./gradlew clean test koverVerify apiCheck dokkaGenerate
./gradlew :benchmark:benchmark
./gradlew :examples:wardrobe-cli:run --args='--weather rain --temperature 8 --template separates --solutions 3 --trace'
./gradlew publishToMavenLocal
```

Expected:

- tests and coverage pass;
- API matches reviewed signatures;
- Dokka resolves public symbols;
- benchmark JSON and Markdown are generated;
- CLI emits only feasible IDs;
- five Maven modules include sources and Javadocs.

Smart Wardrobe:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Emulator scenarios:

1. Home under clear, rain, and cold weather.
2. Fresh Looks excludes laundry.
3. Inspiration uses only owned IDs.
4. Daily worker runs without an activity.
5. Travel respects trip inventory and slots.
6. Locked sets remain atomic.
7. Ranker failure returns deterministic local results.
8. APK contains no Gemini API key.

---

## 8. Release Sequence

1. Tag `0.1.0-alpha.1` after standalone correctness, CLI, docs, and synthetic benchmarks pass.
2. Integrate the alpha through a local composite build.
3. Run shadow mode without changing user-visible output.
4. Fix engine defects in the public repository and consume new alpha tags.
5. Promote Home, then Fresh Looks, Inspiration, worker, and travel.
6. Publish a redacted case study.
7. Freeze API changes and publish `0.1.0-rc.1`.
8. Complete security, dependency, license, APK-secret, and benchmark review.
9. Publish `0.1.0` only after explicit approval for GitHub and Maven Central publication.

Versioning:

- `0.x`: API changes require migration notes.
- `1.0.0`: solver contracts, ranker SPI, and trace schema become compatibility commitments.
- Constraint behavior changes require release notes even without signature changes.
- Benchmark methodology changes create a new report version and never overwrite old results.

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| Combinatorial explosion | Slot pruning, beam search, node/time budgets, honest termination, benchmarks. |
| False infeasibility | Return `INFEASIBLE` only after exhaustive search. |
| Invented LLM IDs | Rank known `SolutionId` values only, validate strictly, fall back locally. |
| Prompt injection | JSON separation, escaping, no notes by default, strict output schema. |
| Over-generalized abstractions | Keep core minimal and fashion as the first real module. |
| Android leakage | Pure JVM core, injected transport, app-side mapper. |
| Private benchmark data | Seeded synthetic fixtures and aggregated redacted case study. |
| Valid but unattractive results | Local soft scorers plus optional ranking of feasible solutions. |
| Callers bypass constraints | One gateway and source architecture test. |
| Existing Room migration risk | Add no engine persistence initially; separately fix and test missing 28→29 migration before new columns. |
| Credential exposure | No credential API in engine; scoped CI secrets and APK/repository scans. |

---

## 10. Definition of Done

`0.1.0` is complete when:

- a clean clone builds with JDK 17;
- all invariants have automated coverage;
- exact and bounded semantics are documented and tested;
- the CLI works without cloud services;
- Gemini can rank but cannot introduce candidates;
- fashion policies contain no Smart Wardrobe classes;
- declared baselines have reproducible benchmark reports;
- architecture, constraints, privacy, security, contribution, governance, and Android integration docs are complete;
- API and dependency integrity gates pass;
- Maven-local consumption is verified;
- Smart Wardrobe uses one gateway for all five generation flows;
- replay and emulator checks show zero hard violations;
- no private user data or client Gemini key is present in the public repository or APK;
- public publication occurs only after explicit owner approval.

---

## 11. Self-Review

- **Scope:** One coherent v0.1 subsystem: finite-inventory constrained generation with optional rank-only LLM participation. Model training and general agent workflows are excluded.
- **Coverage:** Bootstrap, API, constraints, solver, bounded semantics, ranking, Gemini, fashion, CLI, tests, benchmarks, docs, CI, publication, privacy, and app integration have explicit tasks.
- **Type consistency:** Core IDs, candidate, slot, assembly, constraint, score, solve, rank, and result names remain consistent throughout.
- **Safety:** Rank-only remote participation, injected credentials, cancellation propagation, honest bounded termination, and publication approval are explicit.
- **Portability:** Standalone paths are relative; Smart Wardrobe paths appear only in the integration section.
