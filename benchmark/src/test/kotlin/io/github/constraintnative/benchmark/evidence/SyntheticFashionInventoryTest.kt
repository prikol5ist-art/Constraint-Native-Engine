package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.fashion.FashionSlots
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SyntheticFashionInventoryTest {
    @Test
    fun `same seed creates the same candidates`() {
        val first = SyntheticFashionInventory.create(500, 20260719L)
        val replay = SyntheticFashionInventory.create(500, 20260719L)
        assertThat(replay).isEqualTo(first)
    }

    @Test
    fun `every workload supports dress and separates templates`() {
        listOf(25, 100, 500, 1_000).forEach { size ->
            val candidates = SyntheticFashionInventory.create(size, 20260719L)
            val slots = candidates.flatMap { it.eligibleSlots }.toSet()
            assertThat(slots).contains(
                FashionSlots.TOP,
                FashionSlots.BOTTOM,
                FashionSlots.FULL_BODY,
                FashionSlots.FOOTWEAR
            )
            assertThat(candidates).hasSize(size)
            assertThat(candidates.map { it.id }).doesNotHaveDuplicates()
        }
    }
}
