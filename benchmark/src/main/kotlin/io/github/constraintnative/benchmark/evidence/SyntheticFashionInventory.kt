package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.core.Candidate
import io.github.constraintnative.core.CandidateId
import io.github.constraintnative.fashion.FashionCandidate
import io.github.constraintnative.fashion.FashionSlots
import kotlin.random.Random

object SyntheticFashionInventory {
    fun create(size: Int, seed: Long): List<Candidate<FashionCandidate>> {
        require(size >= 20) { "size must be at least 20" }
        val random = Random(seed)
        val colors = listOf("black", "white", "navy", "blue", "red", "green", "grey", "brown")
        val materials = listOf("cotton", "linen", "denim", "wool", "leather", "viscose")
        val silhouettes = listOf("regular", "slim", "wide", "oversized")

        return List(size) { index ->
            val slot = when (index % 20) {
                in 0..5 -> FashionSlots.TOP
                in 6..10 -> FashionSlots.BOTTOM
                in 11..13 -> FashionSlots.FULL_BODY
                in 14..15 -> FashionSlots.FOOTWEAR
                in 16..17 -> FashionSlots.OUTERWEAR
                else -> FashionSlots.ACCESSORY
            }
            val category = slot.value
            val type = when (slot) {
                FashionSlots.TOP -> listOf("shirt", "t_shirt", "sweater")[index % 3]
                FashionSlots.BOTTOM -> listOf("pants", "jeans", "skirt")[index % 3]
                FashionSlots.FULL_BODY -> "dress"
                FashionSlots.FOOTWEAR -> listOf("sneakers", "boots")[index % 2]
                FashionSlots.OUTERWEAR -> listOf("jacket", "coat")[index % 2]
                else -> listOf("scarf", "hat")[index % 2]
            }
            val id = "item-${index.toString().padStart(4, '0')}"
            Candidate(
                id = CandidateId(id),
                attributes = FashionCandidate(
                    id = id,
                    name = "Synthetic $type $index",
                    type = type,
                    color = colors[random.nextInt(colors.size)],
                    layer = if (slot == FashionSlots.OUTERWEAR) 2 else 1,
                    formalness = (1 + random.nextInt(10)) / 10.0,
                    category = category,
                    material = materials[random.nextInt(materials.size)],
                    silhouette = silhouettes[random.nextInt(silhouettes.size)],
                    warmth = 1 + random.nextInt(10),
                    breathability = 1 + random.nextInt(10),
                    styleScores = mapOf("minimalist" to random.nextInt(4).toDouble()),
                    available = true,
                    inLaundry = false,
                    wearCount = random.nextInt(20),
                    favorite = random.nextBoolean()
                ),
                eligibleSlots = setOf(slot)
            )
        }
    }
}
