package io.github.constraintnative.examples.wardrobe

import io.github.constraintnative.fashion.Occasion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CliArgumentsTest {

    @Test
    fun `parses documented valid arguments`() {
        assertThat(parseCliArguments(arrayOf("--occasion=Formal", "--temperature=12.0", "--diversityWeight=0.5")))
            .isEqualTo(CliParseResult.Valid(CliOptions(Occasion.FORMAL, 12.0, 0.5)))
    }

    @Test
    fun `rejects invalid numeric and unknown arguments`() {
        assertThat(parseCliArguments(arrayOf("--temperature=NaN")))
            .isEqualTo(CliParseResult.Invalid("--temperature must be finite"))
        assertThat(parseCliArguments(arrayOf("--temperature=Infinity")))
            .isEqualTo(CliParseResult.Invalid("--temperature must be finite"))
        assertThat(parseCliArguments(arrayOf("--diversityWeight=1.5")))
            .isEqualTo(CliParseResult.Invalid("--diversityWeight must be in 0.0..1.0"))
        assertThat(parseCliArguments(arrayOf("--unknown")))
            .isEqualTo(CliParseResult.Invalid("Unknown argument: --unknown"))
    }

    @Test
    fun `rejects missing value and invalid occasion`() {
        assertThat(parseCliArguments(arrayOf("--occasion")))
            .isEqualTo(CliParseResult.Invalid("Missing value for --occasion"))
        assertThat(parseCliArguments(arrayOf("--occasion=invalid")))
            .isEqualTo(CliParseResult.Invalid("--occasion must be casual, formal, sporty, or business"))
    }
}
