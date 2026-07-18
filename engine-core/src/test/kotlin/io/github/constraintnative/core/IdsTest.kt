package io.github.constraintnative.core

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class IdsTest {

    @Test
    fun `should accept valid IDs`() {
        assertDoesNotThrow { CandidateId("abc") }
        assertDoesNotThrow { SlotId("SLOT-123") }
        assertDoesNotThrow { GroupId("group.name_45") }
        assertDoesNotThrow { SolutionId("sol_id") }
    }

    @Test
    fun `should reject blank or empty IDs`() {
        assertThatThrownBy { CandidateId("") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SlotId("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should reject untrimmed IDs`() {
        assertThatThrownBy { GroupId(" trimmed ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SolutionId("trimmed ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { CandidateId(" trimmed") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should reject IDs longer than 128 characters`() {
        val longId = "a".repeat(129)
        assertThatThrownBy { CandidateId(longId) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should reject control characters`() {
        assertThatThrownBy { CandidateId("abc\n") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SlotId("abc\u0000def") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { GroupId("abc\t") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
