package io.github.constraintnative.examples.wardrobe

import io.github.constraintnative.core.*
import io.github.constraintnative.solver.DeterministicSolver
import io.github.constraintnative.generative.*
import io.github.constraintnative.fashion.*
import kotlinx.coroutines.runBlocking

public fun main(args: Array<String>) {
    val options = when (val parsed = parseCliArguments(args)) {
        is CliParseResult.Valid -> parsed.options
        is CliParseResult.Invalid -> {
            System.err.println(parsed.message)
            System.exit(2)
            return
        }
    }

    val context = FashionContext(
        temperatureCelsius = options.temperatureCelsius,
        precipitation = Precipitation.NONE,
        occasion = options.occasion
    )

    // Setup wardrobe candidates with explicit type arguments
    val candidates: List<Candidate<FashionCandidate>> = listOf(
        // Tops
        Candidate(
            id = CandidateId("t-shirt"),
            attributes = FashionCandidate("t-shirt", "Casual T-Shirt", "T-Shirt", "red", 1, 0.2),
            eligibleSlots = setOf(FashionSlots.TOP)
        ),
        Candidate(
            id = CandidateId("shirt"),
            attributes = FashionCandidate("shirt", "Formal Button Shirt", "Shirt", "white", 1, 0.8),
            eligibleSlots = setOf(FashionSlots.TOP)
        ),
        Candidate(
            id = CandidateId("sweater"),
            attributes = FashionCandidate("sweater", "Comfy Sweater", "Sweater", "grey", 1, 0.5),
            eligibleSlots = setOf(FashionSlots.TOP)
        ),
        // Bottoms
        Candidate(
            id = CandidateId("jeans"),
            attributes = FashionCandidate("jeans", "Classic Jeans", "Jeans", "blue", 1, 0.3),
            eligibleSlots = setOf(FashionSlots.BOTTOM)
        ),
        Candidate(
            id = CandidateId("trousers"),
            attributes = FashionCandidate("trousers", "Formal Trousers", "Trousers", "black", 1, 0.7),
            eligibleSlots = setOf(FashionSlots.BOTTOM)
        ),
        // Outerwear
        Candidate(
            id = CandidateId("raincoat"),
            attributes = FashionCandidate("raincoat", "Bright Raincoat", "Raincoat", "orange", 2, 0.3),
            eligibleSlots = setOf(FashionSlots.OUTERWEAR)
        ),
        Candidate(
            id = CandidateId("blazer"),
            attributes = FashionCandidate("blazer", "Sharp Blazer", "Blazer", "navy", 2, 0.8),
            eligibleSlots = setOf(FashionSlots.OUTERWEAR)
        ),
        // Footwear
        Candidate(
            id = CandidateId("sneakers"),
            attributes = FashionCandidate("sneakers", "Sporty Sneakers", "Sneakers", "white", 1, 0.2),
            eligibleSlots = setOf(FashionSlots.FOOTWEAR)
        ),
        Candidate(
            id = CandidateId("oxfords"),
            attributes = FashionCandidate("oxfords", "Leather Oxfords", "Oxfords", "brown", 1, 0.9),
            eligibleSlots = setOf(FashionSlots.FOOTWEAR)
        )
    )

    // Slots
    val slots = listOf(
        SlotRequirement(FashionSlots.TOP, 1, 1),
        SlotRequirement(FashionSlots.BOTTOM, 1, 1),
        SlotRequirement(FashionSlots.OUTERWEAR, 0, 1),
        SlotRequirement(FashionSlots.FOOTWEAR, 1, 1)
    )

    // Hard constraints
    val hardConstraints: List<HardConstraint<FashionCandidate, FashionContext>> = listOf(
        ForbiddenPairConstraint(CandidateId("t-shirt"), CandidateId("blazer"))
    )

    // Soft scorers
    val softScorers: List<SoftScorer<FashionCandidate, FashionContext>> = listOf(
        TemperatureScorer(),
        OccasionScorer(),
        LayerScorer(),
        ColorHarmonyScorer()
    )

    val budget = SearchBudget(
        maxExpandedNodes = 2000L,
        maxDurationMillis = 5000L,
        beamWidth = 10
    )

    val solveRequest = SolveRequest(
        candidates = candidates,
        slots = slots,
        context = context,
        hardConstraints = hardConstraints,
        softScorers = softScorers,
        desiredSolutions = 5,
        searchMode = SearchMode.EXACT,
        searchBudget = budget
    )

    println("====================================================")
    println(" Wardrobe Constraint Native Engine CLI")
    println(" Occasion: ${options.occasion}")
    println(" Temperature: ${options.temperatureCelsius} °C")
    println(" Diversity Weight: ${options.diversityWeight}")
    println("====================================================")

    val engine = ConstraintNativeEngine(DeterministicSolver())

    val result = try {
        runBlocking {
            engine.generate(
                request = solveRequest,
                ranker = null,
                renderer = null,
                diversityWeight = options.diversityWeight
            )
        }
    } catch (e: Exception) {
        System.err.println("Search/Ranking error: ${e.message}")
        e.printStackTrace()
        System.exit(1)
        return
    }

    println("\nSearch Finished with termination: ${result.termination}")
    println("Ranker status: ${result.rankerStatus}")
    if (result.rankerError != null) {
        System.err.println("Ranker warning/error: ${result.rankerError}")
    }

    println("\nFeasible Outfits Found: ${result.solutions.size}")
    result.solutions.forEachIndexed { index, solution ->
        println("\nOutfit #${index + 1} (Score: ${solution.totalScore}, ID: ${solution.id.value})")
        solution.assignments.forEach { ass ->
            val c = ass.candidate.attributes
            println("  - [${ass.slot.value}] ${c.name} (${c.type}, color=${c.color}, formalness=${c.formalness})")
        }
        println("  Score Components:")
        solution.scoreComponents.forEach { comp ->
            println("    * ${comp.name}: ${comp.value}")
        }
        if (solution.explanation != null) {
            println("  AI Explanation: ${solution.explanation}")
        }
    }

    System.exit(0)
}
