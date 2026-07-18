package io.github.constraintnative.examples.wardrobe

import io.github.constraintnative.core.*
import io.github.constraintnative.solver.DeterministicSolver
import io.github.constraintnative.generative.*
import io.github.constraintnative.gemini.GeminiGenerativeRanker
import io.github.constraintnative.gemini.GeminiTransport
import io.github.constraintnative.fashion.*
import kotlinx.coroutines.runBlocking

public fun main(args: Array<String>) {
    var apiKey: String? = null
    var occasionStr: String = "Casual"
    var tempVal: Double = 20.0
    var divWeight: Double = 0.0

    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg.startsWith("--apiKey=") -> apiKey = arg.substringAfter("=")
            arg == "--apiKey" && i + 1 < args.size -> {
                apiKey = args[i + 1]
                i++
            }
            arg.startsWith("--occasion=") -> occasionStr = arg.substringAfter("=")
            arg == "--occasion" && i + 1 < args.size -> {
                occasionStr = args[i + 1]
                i++
            }
            arg.startsWith("--temperature=") -> tempVal = arg.substringAfter("=").toDoubleOrNull() ?: 20.0
            arg == "--temperature" && i + 1 < args.size -> {
                tempVal = args[i + 1].toDoubleOrNull() ?: 20.0
                i++
            }
            arg.startsWith("--diversityWeight=") -> divWeight = arg.substringAfter("=").toDoubleOrNull() ?: 0.0
            arg == "--diversityWeight" && i + 1 < args.size -> {
                divWeight = args[i + 1].toDoubleOrNull() ?: 0.0
                i++
            }
        }
        i++
    }

    val occasion = when (occasionStr.lowercase()) {
        "formal" -> Occasion.FORMAL
        "sporty" -> Occasion.SPORTY
        "business" -> Occasion.BUSINESS
        else -> Occasion.CASUAL
    }

    val context = FashionContext(
        temperatureCelsius = tempVal,
        precipitation = Precipitation.NONE,
        occasion = occasion
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
        searchBudget = budget,
        seed = 42L
    )

    println("====================================================")
    println(" Wardrobe Constraint Native Engine CLI")
    println(" Occasion: $occasion")
    println(" Temperature: $tempVal °C")
    println(" Diversity Weight: $divWeight")
    println("====================================================")

    val ranker = if (apiKey != null) {
        println("Initializing real GeminiGenerativeRanker via HTTP...")
        val transport = GeminiTransport { requestJson ->
            val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { os ->
                os.write(requestJson.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status != 200) {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw RuntimeException("HTTP error $status: $errorMsg")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        }
        GeminiGenerativeRanker(transport)
    } else {
        println("No --apiKey provided. Initializing dummy fallback ranker...")
        GenerativeRanker { request ->
            val ranked = request.solutions.mapIndexed { idx, sol ->
                RankedSolution(
                    id = sol.id,
                    explanation = "Dummy explanation: outfit matches $occasion at $tempVal°C (ranked #$idx)"
                )
            }
            RankingResponse(ranked)
        }
    }

    val engine = ConstraintNativeEngine(DeterministicSolver())
    val renderer = FashionContextRenderer()

    val result = try {
        runBlocking {
            engine.generate(
                request = solveRequest,
                ranker = ranker,
                renderer = renderer,
                diversityWeight = divWeight
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
